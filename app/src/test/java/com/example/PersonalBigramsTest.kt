package com.example

import com.example.keyboard.PersonalBigrams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersonalBigramsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "bigrams.tsv")

    @Test fun par_repetido_vira_previsao_e_peso() {
        val b = PersonalBigrams(file())
        repeat(3) { b.record("bom", "dia") }
        assertEquals(listOf("dia"), b.topNext("bom", 3))
        assertTrue(b.boost("bom", "dia") > 0)
        assertEquals(0, b.boost("bom", "noite"))
    }

    @Test fun persiste_entre_instancias() {
        PersonalBigrams(file()).record("oi", "tudo")
        assertEquals(listOf("tudo"), PersonalBigrams(file()).topNext("oi", 3))
    }

    @Test fun decaimento_remove_pares_que_zeram() {
        val b = PersonalBigrams(file())
        b.record("a", "b")
        b.decay(0.5f)
        assertEquals(0, b.size)
    }

    @Test fun limite_de_pares() {
        val b = PersonalBigrams(file(), maxPairs = 2)
        b.record("a", "b"); b.record("a", "c"); b.record("a", "d")
        assertEquals(2, b.size)
    }

    @Test fun chave_vazia_nao_registra() {
        val b = PersonalBigrams(file())
        b.record("", "x")
        assertEquals(0, b.size)
    }
}
