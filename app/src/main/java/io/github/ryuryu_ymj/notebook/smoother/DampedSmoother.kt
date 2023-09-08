package io.github.ryuryu_ymj.notebook.smoother

import io.github.ryuryu_ymj.notebook.Pen
import io.github.ryuryu_ymj.notebook.Stroke
import kotlin.math.hypot
import kotlin.math.min

class DampedSmoother : TouchSmoother {
  private var touchX = 0f
  private var touchY = 0f
  private var touchDX = 0f
  private var touchDY = 0f
  private var nibX = 0f
  private var nibY = 0f
  private var nibDX = 0f
  private var nibDY = 0f
  private var nibPressure = 0f
  private var time = 0L
  private val error = 0.1f

  override fun beginStroke(
    stroke: Stroke,
    x: Float,
    y: Float,
    pressure: Float,
    time: Long,
    pen: Pen
  ) {
    touchX = x
    touchY = y
    nibPressure = pressure
    nibX = x
    nibY = y
    nibDX = 0f
    nibDY = 0f
    this.time = time
    pen.begin(stroke, x, y, nibPressure)
  }

  override fun extendStroke(
      stroke: Stroke,
      x: Float,
      y: Float,
      pressure: Float,
      time: Long,
      pen: Pen
  ) {
    val w1 = 0.5f
    val duration = time - this.time
    val newTouchDX = (x - touchX) / duration
    val newTouchDY = (y - touchY) / duration
    touchDX = touchDX * (1 - w1) + newTouchDX * w1
    touchDY = touchDY * (1 - w1) + newTouchDY * w1

    touchX = x
    touchY = y
    moveNibPhysically(duration.toInt())
    this.time = time

    val w2 = 0.2f
    nibPressure = nibPressure * (1 - w2) + pressure * w2

    pen.move(stroke, nibX, nibY, nibPressure)
  }

  override fun endStroke(stroke: Stroke, pen: Pen) {
    var move = hypot(nibX - touchX, nibY - touchY)
    val dt = 4
    while (move > error) {
      touchX += touchDX * dt
      touchY += touchDY * dt
      moveNibPhysically(dt)
      move -= hypot(nibDX * dt, nibDY * dt)

      pen.move(stroke, nibX, nibY, nibPressure)
    }

    pen.end(stroke)
  }

  private fun moveNibPhysically(duration: Int) {
    var timeLeft = duration
    val omega = 0.055f
    val gamma = 0.45f // damping ratio
    while (timeLeft > 0) {
      val dst = hypot(nibX - touchX, nibY - touchY)
      if (dst <= error) break
      val dt = min(4, timeLeft)
      timeLeft -= dt
      nibDX -= ((nibX - touchX) * omega * omega + nibDX * omega * gamma * 2) * dt
      nibDY -= ((nibY - touchY) * omega * omega + nibDY * omega * gamma * 2) * dt
      nibX += nibDX * dt
      nibY += nibDY * dt
    }
  }
}
