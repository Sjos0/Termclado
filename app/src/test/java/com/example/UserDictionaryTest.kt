package com.example

import com.example.keyboard.UserDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserDictionaryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(): File = File(tmp.root, "user_dict.tsv")

    @Test fun aprende_e_reconhece_sem_diferenciar_maiusculas() {
        val d = UserDictionary(file())
        d.learn("Bicicleta")
        assertTrue(d.contains("bicicleta"))
        assertEquals(listOf("Bicicleta"), d.suggestionsFor("bici"))
    }

    @Test fun persiste_entre_instancias() {
        UserDictionary(file()).learn("xyzt")
        assertTrue(UserDictionary(file()).contains("xyzt"))
    }

    @Test fun clear_remove_tudo() {
        val d = UserDictionary(file())
        d.learn("abc")
        d.clear()
        assertFalse(d.contains("abc"))
        assertFalse(file().exists())
    }

    @Test fun limite_remove_as_menos_usadas() {
        val d = UserDictionary(file(), maxWords = 2)
        d.learn("aaa"); d.learn("aaa"); d.learn("bbb"); d.learn("ccc")
        assertEquals(2, d.size)
        assertTrue(d.contains("aaa"))
    }

    @Test fun palavra_curta_nao_e_aprendida() {
        val d = UserDictionary(file())
        d.learn("a")
        assertEquals(0, d.size)
    }
}
