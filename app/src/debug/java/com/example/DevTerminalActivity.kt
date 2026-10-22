package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/** Só depuração: imita o campo do Termux (TYPE_NULL, sem composição, sem apagar por texto). */
class DevTerminalActivity : Activity() {
    private class FakeTerminal(ctx: Context) : android.widget.EditText(ctx) {
        val buffer = StringBuilder()
        init { inputType = InputType.TYPE_NULL; isFocusable = true; isFocusableInTouchMode = true }
        override fun onCheckIsTextEditor() = true
        private fun log() = Log.i("TermTest", "text=$buffer")
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
            outAttrs.inputType = InputType.TYPE_NULL
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
            return object : BaseInputConnection(this, true) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { buffer.append(text); log(); return true }
                override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = true   // o Termux não mostra composição
                override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
                override fun deleteSurroundingText(before: Int, after: Int) = true                  // o Termux ignora
                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.keyCode == KeyEvent.KEYCODE_DEL && buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                        if (event.keyCode == KeyEvent.KEYCODE_ENTER) buffer.append("\n")
                        log()
                    }
                    return true
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = FakeTerminal(this); setContentView(v)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        v.postDelayed({ v.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(v, InputMethodManager.SHOW_FORCED) }, 800)
    }
}
