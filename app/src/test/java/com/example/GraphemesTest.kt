package com.example

import com.example.keyboard.Graphemes
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemesTest {
    @Test fun letra_ascii_apaga_um_caractere() = assertEquals(1, Graphemes.lastClusterLength("abc"))
    @Test fun acento_latino_apaga_um_caractere() = assertEquals(1, Graphemes.lastClusterLength("ação"))
    @Test fun emoji_simples_apaga_o_par_inteiro() = assertEquals(2, Graphemes.lastClusterLength("a😀"))
    @Test fun emoji_com_tom_de_pele_apaga_a_sequencia() = assertEquals(4, Graphemes.lastClusterLength("oi👍🏽"))
    @Test fun sequencia_zwj_apaga_tudo_de_uma_vez() = assertEquals(5, Graphemes.lastClusterLength("x👨\u200D👩"))
    @Test fun seletor_de_variacao_pertence_ao_caractere_anterior() = assertEquals(2, Graphemes.lastClusterLength("❤\uFE0F"))
    @Test fun texto_vazio_nao_apaga_nada() = assertEquals(0, Graphemes.lastClusterLength(""))
}
