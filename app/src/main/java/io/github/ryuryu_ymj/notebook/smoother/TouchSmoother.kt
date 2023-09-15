package io.github.ryuryu_ymj.notebook.smoother

typealias OnPointsAdd = (x: Float, y: Float, pressure: Float) -> Unit

interface TouchSmoother {
  fun beginTouch(x: Float, y: Float, pressure: Float, time: Long, onPointsAdd: OnPointsAdd)
  fun moveTouch(x: Float, y: Float, pressure: Float, time: Long, onPointsAdd: OnPointsAdd)
  fun endTouch(onPointsAdd: OnPointsAdd)
}
