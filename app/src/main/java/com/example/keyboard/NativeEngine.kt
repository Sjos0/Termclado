package com.example.keyboard

import android.content.Context
import android.util.Log

/**
 * Motor de sugestões e autocorreção do teclado, em Rust (libkeyboard_core.so).
 *
 * Escolhido por medição (ver scripts/perf e o histórico do PR #24): a autocorreção em Rust leva
 * ~0,1 ms, contra ~3 ms do motor Kotlin que existia antes, e a RAM do motor é equivalente.
 *
 * Se a biblioteca não carregar, o teclado continua funcionando, só sem sugestões e sem autocorreção.
 */
object NativeEngine {
    private const val TAG = "NativeEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("keyboard_core")
            isNativeLoaded = true
        } catch (t: Throwable) {
            Log.e(TAG, "libkeyboard_core.so indisponível: ${t.message}. O teclado funciona sem sugestões.")
        }
    }

    /** Carrega o dicionário binário (assets/dict_pt_br.bin) no motor Rust. */
    fun init(context: Context): Boolean {
        if (!isNativeLoaded) return false
        return try {
            val bytes = context.assets.open("dict_pt_br.bin").use { it.readBytes() }
            val ok = nativeInit(bytes)
            if (!ok) Log.e(TAG, "nativeInit recusou dict_pt_br.bin")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar dict_pt_br.bin: ${e.message}", e)
            false
        }
    }

    fun getSuggestions(prevWord: String, prefix: String): List<String> {
        if (!isNativeLoaded) return emptyList()
        return try {
            nativeGetSuggestions(prevWord, prefix)?.toList() ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "Falha na chamada JNI nativeGetSuggestions: ${t.message}")
            emptyList()
        }
    }

    fun getAutoCorrection(prevWord: String, typedWord: String): String? {
        if (!isNativeLoaded) return null
        return try {
            nativeGetAutoCorrection(prevWord, typedWord)
        } catch (t: Throwable) {
            Log.w(TAG, "Falha na chamada JNI nativeGetAutoCorrection: ${t.message}")
            null
        }
    }

    fun isWord(word: String): Boolean {
        if (!isNativeLoaded) return false
        return try {
            nativeIsWord(word)
        } catch (t: Throwable) {
            Log.w(TAG, "Falha na chamada JNI nativeIsWord: ${t.message}")
            false
        }
    }

    // Declarações nativas em Rust (JNI), ver rust-core/src/jni.rs
    private external fun nativeInit(data: ByteArray): Boolean
    private external fun nativeGetSuggestions(prevWord: String, prefix: String): Array<String>?
    private external fun nativeGetAutoCorrection(prevWord: String, typedWord: String): String?
    private external fun nativeIsWord(word: String): Boolean
}
