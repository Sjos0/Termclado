package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapGateTest {

    @Test
    fun primeiro_toque_sempre_e_aceito() {
        assertTrue(TapGate().accept(1000L))
    }

    @Test
    fun toque_em_menos_de_80ms_e_desconsiderado() {
        val gate = TapGate()
        assertTrue(gate.accept(1000L))
        assertFalse(gate.accept(1079L))
    }

    @Test
    fun toque_em_exatos_80ms_e_aceito() {
        val gate = TapGate()
        assertTrue(gate.accept(1000L))
        assertTrue(gate.accept(1080L))
    }

    @Test
    fun toque_rejeitado_nao_reinicia_a_janela() {
        val gate = TapGate()
        assertTrue(gate.accept(0L))
        assertFalse(gate.accept(50L))
        assertTrue(gate.accept(85L))
    }

    @Test
    fun a_janela_e_global_entre_botoes_diferentes() {
        // Não há chave: qualquer toque conta. Simula "?123" seguido de "ABC" na mesma posição.
        val gate = TapGate()
        assertTrue(gate.accept(2000L))
        assertFalse(gate.accept(2030L))
    }

    @Test
    fun janela_padrao_e_80ms() {
        assertTrue(TapGate.WINDOW_MS == 80L)
    }
}
