package com.example

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText

/**
 * Tela de teste que existe SOMENTE no build de depuração (src/debug).
 * Mostra um campo de texto com o teclado sempre visível, para os testes automatizados no emulador.
 */
class DevTypingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val edit = EditText(this).apply {
            hint = "campo de teste"
            setPadding(40, 160, 40, 40)
        }
        setContentView(edit)

        // Modo benchmark: mede o motor e sai, sem o IME (ver scripts/perf/medir.sh)
        if (intent?.getBooleanExtra("bench", false) == true) {
            EngineBench.run(this)
            finish()
            return
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        edit.post {
            edit.requestFocus()
            // Mostra o teclado de verdade (o IME precisa estar visível para medir o serviço)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_FORCED)
        }
    }
}
