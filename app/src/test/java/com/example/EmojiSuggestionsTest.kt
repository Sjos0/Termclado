package com.example

import com.example.keyboard.EmojiSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiSuggestionsTest {

    private val table: Map<String, List<String>> by lazy {
        // Testes unitários rodam na pasta do módulo app/
        val file = File("src/main/assets/emoji_pt_br.tsv")
        assertTrue("tabela de emoji não encontrada em ${file.absolutePath}", file.exists())
        EmojiSuggestions.parse(file.readLines().asSequence())
    }

    @Test
    fun teclado_sugere_teclado_com_nome_oficial_primeiro_e_piano_tambem() {
        val emojis = table[EmojiSuggestions.normalize("teclado")] ?: error("sem emojis para teclado")
        assertTrue("primeiro deve ser ⌨ (nome oficial), veio ${emojis.first().codePoints().toArray().toList()}",
            emojis.first().startsWith("\u2328"))
        assertTrue("deve incluir 🎹", emojis.any { it.startsWith("\uD83C\uDFB9") })
    }

    @Test
    fun piano_sugere_piano_musical() {
        val emojis = table[EmojiSuggestions.normalize("piano")]
        assertTrue(emojis != null && emojis.isNotEmpty())
    }

    @Test
    fun palavra_sem_emoji_nao_sugere_nada() {
        assertTrue(table[EmojiSuggestions.normalize("xyzzy")] == null)
    }

    @Test
    fun acento_e_maiusculas_nao_importam() {
        assertEquals(table[EmojiSuggestions.normalize("digitação")], table[EmojiSuggestions.normalize("DIGITACAO")])
    }
}
