package io.github.ryuryu_ymj.notebook.gestures

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastForEach

class DeltaVelocityTracker {
  private val velocityTracker = VelocityTracker()

  private var currentPointerPositionAccumulator = Offset.Zero

  private fun addDelta(timeMillis: Long, delta: Offset) {
    currentPointerPositionAccumulator += delta
    velocityTracker.addPosition(timeMillis, currentPointerPositionAccumulator)
  }

  /**
   * Computes the estimated velocity of the pointer at the time of the last provided data point.
   *
   * This can be expensive. Only call this when you need the velocity.
   */
  fun calculateVelocity() = velocityTracker.calculateVelocity()

  fun resetTracking() {
    currentPointerPositionAccumulator = Offset.Zero
    velocityTracker.resetTracking()
  }

  @OptIn(ExperimentalComposeUiApi::class)
  fun addMultiPointerInputChanges(changes: List<PointerInputChange>) {
    if (changes.isEmpty()) return

    var delta = Offset.Zero
    var size = 0
    changes.fastForEach {
      if (it.previousPressed && it.historical.isNotEmpty()) {
        delta += it.historical[0].position - it.previousPosition
        size++
      }
    }
    if (size > 0) {
      delta /= size.toFloat()
      addDelta(changes[0].historical[0].uptimeMillis, delta)
    }

    for (i in 1..changes[0].historical.lastIndex) {
      delta = Offset.Zero
      changes.fastForEach { delta += it.historical[i].position - it.historical[i - 1].position }
      delta /= changes.size.toFloat()
      addDelta(changes[0].historical[i].uptimeMillis, delta)
    }

    delta = Offset.Zero
    size = 0
    changes.fastForEach {
      if (it.historical.isNotEmpty()) {
        delta += it.position - it.historical.last().position
        size++
      }
    }
    if (size > 0) {
      delta /= changes.size.toFloat()
      addDelta(changes[0].uptimeMillis, delta)
    }
  }
}
