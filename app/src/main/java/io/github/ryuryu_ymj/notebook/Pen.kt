package io.github.ryuryu_ymj.notebook

class Pen(private val strokeWidth: Float = 3f) {
  private fun pressureToRadius(pressure: Float) = strokeWidth * (0.3f + pressure * 0.4f) / 2

  fun begin(stroke: Stroke, x: Float, y: Float, pressure: Float) {
    stroke.begin(x, y, pressureToRadius(pressure))
  }

  fun move(stroke: Stroke, x: Float, y: Float, pressure: Float) {
    stroke.extend(x, y, pressureToRadius(pressure))
  }

  fun end(stroke: Stroke) {
    stroke.end()
  }
}
