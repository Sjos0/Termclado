package com.example

import android.content.Context
import android.os.Debug
import android.util.Log
import com.example.keyboard.NativeEngine

/**
 * Benchmark e teste de equivalência do motor, SOMENTE no build de depuração.
 *
 * - MEM: memória Java e nativa (malloc, onde fica o Rust) antes e depois de carregar o motor.
 * - AC / SG: autocorreção e sugestões para cada palavra de equiv_words.txt.
 * Os resultados vão para o logcat (tag EngineBench), e o script compara os motores.
 */
object EngineBench {
    private const val TAG = "EngineBench"
    @Volatile private var done = false

    private fun javaUsedKb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024
    }

    private fun settle() {
        Runtime.getRuntime().gc()
        Thread.sleep(200)
        Runtime.getRuntime().gc()
    }

    fun run(context: Context) {
        if (done) return
        done = true
        settle()
        val j0 = javaUsedKb()
        val n0 = Debug.getNativeHeapAllocatedSize() / 1024

        val ok = NativeEngine.init(context)

        settle()
        val j1 = javaUsedKb()
        val n1 = Debug.getNativeHeapAllocatedSize() / 1024
        Log.i(TAG, "MEM|ok=$ok|javaDeltaKB=${j1 - j0}|nativeDeltaKB=${n1 - n0}|javaKB=$j1|nativeKB=$n1")

        val words = context.assets.open("equiv_words.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        for (w in words) {
            val ac = NativeEngine.getAutoCorrection("", w) ?: "-"
            Log.i(TAG, "AC|$w|$ac")
        }
        for (w in words) {
            val sg = NativeEngine.getSuggestions("", w).take(3).joinToString(",")
            Log.i(TAG, "SG|$w|$sg")
        }
        // Tempo médio por consulta (2000 chamadas, após aquecimento)
        val pool = words.ifEmpty { listOf("ola") }
        for (i in 0 until 200) { NativeEngine.getSuggestions("", pool[i % pool.size]); NativeEngine.getAutoCorrection("", pool[i % pool.size]) }
        val calls = 2000
        val t0 = System.nanoTime()
        for (i in 0 until calls) NativeEngine.getSuggestions("", pool[i % pool.size])
        val sgUs = (System.nanoTime() - t0) / 1000.0 / calls
        val t1 = System.nanoTime()
        for (i in 0 until calls) NativeEngine.getAutoCorrection("", pool[i % pool.size])
        val acUs = (System.nanoTime() - t1) / 1000.0 / calls
        Log.i(TAG, "TIME|sugestaoUs=${"%.1f".format(sgUs)}|autocorrecaoUs=${"%.1f".format(acUs)}")
        Log.i(TAG, "FIM")
    }
}
