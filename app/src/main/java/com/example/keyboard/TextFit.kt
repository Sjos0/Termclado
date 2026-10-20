package com.example.keyboard

/** Ajuste de tamanho de texto: o maior tamanho (entre min e max) em que a largura medida cabe. */
object TextFit {
    /**
     * [measureAt] recebe um tamanho e devolve a largura do texto nesse tamanho (crescente com o tamanho).
     * Se nem o tamanho mínimo couber, devolve o mínimo (o chamador decide o que fazer com o excesso).
     */
    fun fitSize(max: Float, min: Float, maxWidth: Float, measureAt: (Float) -> Float): Float {
        if (measureAt(max) <= maxWidth) return max
        var lo = min
        var hi = max
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (measureAt(mid) <= maxWidth) lo = mid else hi = mid
        }
        return lo
    }
}
