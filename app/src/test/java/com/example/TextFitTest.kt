package com.example

import com.example.keyboard.TextFit
import org.junit.Assert.assertEquals
import org.junit.Test

class TextFitTest {
    // largura = 10 px por unidade de tamanho (linear, como a medição real)
    private val linear: (Float) -> Float = { size -> size * 10f }

    @Test fun texto_curto_mantem_o_tamanho_maximo() = assertEquals(16f, TextFit.fitSize(16f, 10f, 200f, linear), 0.01f)
    @Test fun texto_longo_reduz_ate_caber() = assertEquals(12f, TextFit.fitSize(16f, 10f, 120f, linear), 0.05f)
    @Test fun nunca_passa_do_minimo() = assertEquals(10f, TextFit.fitSize(16f, 10f, 50f, linear), 0.01f)
}
