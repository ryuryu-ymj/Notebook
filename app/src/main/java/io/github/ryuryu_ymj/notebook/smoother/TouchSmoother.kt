package io.github.ryuryu_ymj.notebook.smoother

import io.github.ryuryu_ymj.notebook.Pen
import io.github.ryuryu_ymj.notebook.Stroke

interface TouchSmoother {
  fun beginStroke(stroke: Stroke, x: Float, y: Float, pressure: Float, time: Long, pen: Pen)
  fun extendStroke(stroke: Stroke, x: Float, y: Float, pressure: Float, time: Long, pen: Pen)
  fun endStroke(stroke: Stroke, pen: Pen)
}
