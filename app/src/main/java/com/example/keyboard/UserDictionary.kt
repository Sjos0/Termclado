package com.example.keyboard

import java.io.File

/**
 * Vocabulário pessoal (#43): palavras que o usuário escolheu explicitamente na barra.
 * Só fica no aparelho (arquivo privado do app); nunca vai para a rede.
 */
class UserDictionary(private val file: File, private val maxWords: Int = 2000) {
    private class Entry(val form: String, var count: Int)

    private val words = LinkedHashMap<String, Entry>()

    init {
        load()
    }

    fun contains(word: String): Boolean = words.containsKey(key(word))

    fun learn(word: String) {
        val t = word.trim()
        if (t.length < 2) return
        val e = words[key(t)]
        if (e == null) words[key(t)] = Entry(t, 1) else e.count++
        evictIfNeeded()
        save()
    }

    fun unlearn(word: String) {
        if (words.remove(key(word)) != null) save()
    }

    fun clear() {
        words.clear()
        file.delete()
    }

    /** Palavras aprendidas que começam com [prefix], das mais usadas para as menos usadas. */
    fun suggestionsFor(prefix: String, limit: Int = 3): List<String> {
        val p = key(prefix)
        if (p.isEmpty()) return emptyList()
        return words.entries.filter { it.key.startsWith(p) }
            .sortedByDescending { it.value.count }
            .take(limit)
            .map { it.value.form }
    }

    val size: Int get() = words.size

    private fun evictIfNeeded() {
        if (words.size <= maxWords) return
        words.entries.sortedBy { it.value.count }
            .take(words.size - maxWords)
            .map { it.key }
            .forEach { words.remove(it) }
    }

    private fun load() {
        if (!file.exists()) return
        file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 3) words[parts[0]] = Entry(parts[1], parts[2].toIntOrNull() ?: 1)
        }
    }

    private fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            for ((k, e) in words) w.write("$k\t${e.form}\t${e.count}\n")
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    companion object {
        fun key(s: String): String = s.trim().lowercase()
    }
}
