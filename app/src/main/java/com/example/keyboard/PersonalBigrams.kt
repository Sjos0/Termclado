package com.example.keyboard

import java.io.File

/**
 * Previsões pessoais (#45): contagem dos pares "palavra anterior -> próxima palavra" do próprio usuário.
 * Local, sem rede. Não é rede neural: contagens, decaimento periódico e limite de pares.
 */
class PersonalBigrams(
    private val file: File,
    private val maxPairs: Int = 3000,
    private val decayEvery: Int = 200,
) {
    private class Entry(val form: String, var count: Int)

    private val pairs = LinkedHashMap<String, Entry>()   // "prev\tnext" normalizados -> forma + contagem
    private var recordsSinceDecay = 0

    init {
        load()
    }

    fun record(prev: String, next: String) {
        val p = key(prev)
        val n = key(next)
        if (p.isEmpty() || n.isEmpty()) return
        val k = "$p\t$n"
        val e = pairs[k]
        if (e == null) pairs[k] = Entry(next.trim(), 1) else e.count++
        recordsSinceDecay++
        if (recordsSinceDecay >= decayEvery) {
            decayCounts(0.9f)
            recordsSinceDecay = 0
        }
        evictIfNeeded()
        save()
    }

    /** Peso pessoal de [candidate] depois de [prev]. 0 se o par nunca foi visto. */
    fun boost(prev: String, candidate: String): Int {
        val p = key(prev)
        val c = key(candidate)
        if (p.isEmpty() || c.isEmpty()) return 0
        return (pairs["$p\t$c"]?.count ?: 0) * BOOST
    }

    /** Próximas palavras mais usadas depois de [prev]. */
    fun topNext(prev: String, limit: Int): List<String> {
        val p = key(prev)
        if (p.isEmpty()) return emptyList()
        return pairs.entries.filter { it.key.startsWith("$p\t") }
            .sortedByDescending { it.value.count }
            .take(limit)
            .map { it.value.form }
    }

    /** Multiplica todas as contagens e remove as que zeram, para o modelo se adaptar. */
    fun decay(factor: Float = 0.9f) {
        decayCounts(factor)
        save()
    }

    fun clear() {
        pairs.clear()
        recordsSinceDecay = 0
        file.delete()
    }

    val size: Int get() = pairs.size

    private fun decayCounts(factor: Float) {
        val it = pairs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next().value
            e.count = (e.count * factor).toInt()
            if (e.count < 1) it.remove()
        }
    }

    private fun evictIfNeeded() {
        if (pairs.size <= maxPairs) return
        pairs.entries.sortedBy { it.value.count }
            .take(pairs.size - maxPairs)
            .map { it.key }
            .forEach { pairs.remove(it) }
    }

    private fun load() {
        if (!file.exists()) return
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 4) pairs["${parts[0]}\t${parts[1]}"] = Entry(parts[2], parts[3].toIntOrNull() ?: 1)
        }
    }

    private fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            for ((k, e) in pairs) {
                val sep = k.indexOf('\t')
                w.write("${k.substring(0, sep)}\t${k.substring(sep + 1)}\t${e.form}\t${e.count}\n")
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        const val BOOST = 50_000
        fun key(s: String): String = s.trim().lowercase()
    }
}
