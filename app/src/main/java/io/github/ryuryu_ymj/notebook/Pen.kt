package io.github.ryuryu_ymj.notebook

import io.github.ryuryu_ymj.notebook.smoother.DampedSmoother
import io.github.ryuryu_ymj.notebook.smoother.TouchSmoother

class Pen(
    val strokeWidth: Float = 3f,
    val smoother: TouchSmoother = DampedSmoother(),
    val pressureToRadius: (Float) -> Float = { strokeWidth * (0.3f + it * 0.4f) / 2 }
)
