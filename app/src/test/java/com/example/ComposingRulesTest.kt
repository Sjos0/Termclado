package com.example

import com.example.keyboard.ComposingRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposingRulesTest {

    @Test
    fun palavra_que_termina_no_cursor_continua_em_composicao() {
        assertTrue(ComposingRules.stillAtCursor("ola", "ola"))
        assertTrue(ComposingRules.stillAtCursor("texto antigo teste", "teste"))
    }

    @Test
    fun cursor_movido_para_outro_ponto_encerra_a_composicao() {
        assertFalse(ComposingRules.stillAtCursor("texto antigo ", "teste"))
    }

    @Test
    fun composicao_do_editor_fora_do_cursor_conta_como_movimento() {
        assertTrue(ComposingRules.compositionElsewhere(candidatesEnd = 10, cursor = 4))
        assertFalse(ComposingRules.compositionElsewhere(candidatesEnd = 10, cursor = 10))
        assertFalse(ComposingRules.compositionElsewhere(candidatesEnd = -1, cursor = 4))
    }

    @Test
    fun sem_palavra_em_composicao_nao_ha_o_que_manter() {
        assertFalse(ComposingRules.stillAtCursor("", ""))
        assertFalse(ComposingRules.stillAtCursor("abc", ""))
    }
}
