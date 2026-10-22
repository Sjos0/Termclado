package com.example

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import android.widget.EditText

/** Só depuração: campo que declara aceitar imagens e registra cada imagem recebida (teste automático). */
class ImageEditText @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : EditText(ctx, attrs) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(outAttrs, arrayOf("image/png", "image/jpeg", "image/gif", "image/webp"))
        return InputConnectionCompat.createWrapper(ic, outAttrs) { info, _, _ ->
            Log.i("ImageTest", "IMAGE_RECEIVED uri=${info.contentUri} mime=${info.description.getMimeType(0)}")
            true
        }
    }
}
