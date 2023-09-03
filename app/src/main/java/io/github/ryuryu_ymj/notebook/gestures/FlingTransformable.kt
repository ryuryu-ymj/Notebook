package io.github.ryuryu_ymj.notebook.gestures

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch

fun Modifier.flingTransformable(
    state: CentroidTransformableState,
    enabled: Boolean = true,
    canTransform: (PointerInputChange) -> Boolean = { true },
    lockRotationOnZoomPan: Boolean = false,
    flingBehavior: FlingBehavior? = null
): Modifier = composed {
  val fling = flingBehavior ?: FlingTransformableDefaults.flingBehavior()
  val transformLogic = rememberUpdatedState(FlingTransformingLogic(state, fling))

  centroidTransformable(
      state,
      enabled = enabled,
      canTransform = canTransform,
      lockRotationOnZoomPan = lockRotationOnZoomPan,
      startTransformImmediately = { transformLogic.value.shouldTransformImmediately() },
      onTransformStopped = { velocity ->
        launch { transformLogic.value.onTransformStopped(velocity) }
      })
}

private class FlingTransformingLogic(
    val centroidTransformableState: CentroidTransformableState,
    val flingBehavior: FlingBehavior,
) {
  fun shouldTransformImmediately(): Boolean {
    return centroidTransformableState.isTransformInProgress
  }

  suspend fun onTransformStopped(initialVelocity: Velocity) {
    centroidTransformableState.transform {
      with(flingBehavior) { performFling(Offset.Zero, initialVelocity, 0f, 0f) }
    }
  }
}

object FlingTransformableDefaults {
  /** Create and remember default [FlingBehavior] that will represent natural fling curve. */
  @Composable
  fun flingBehavior(): FlingBehavior {
    val flingSpec = rememberSplineBasedDecay<Offset>()
    return remember(flingSpec) { DefaultFlingBehavior(flingSpec) }
  }
}

private class DefaultFlingBehavior(private val flingDecay: DecayAnimationSpec<Offset>) :
    FlingBehavior {
  override suspend fun CentroidTransformScope.performFling(
      centroid: Offset,
      panVelocity: Velocity,
      zoomVelocity: Float,
      rotationVelocity: Float
  ) {
    var lastValue = Offset.Zero
    AnimationState(
            typeConverter = Offset.VectorConverter,
            initialValue = lastValue,
            initialVelocityVector = AnimationVector2D(panVelocity.x, panVelocity.y))
        .animateDecay(flingDecay) {
          transformBy(
              centroid = centroid,
              panChange = value - lastValue,
          )
          lastValue = value
        }
  }
}
