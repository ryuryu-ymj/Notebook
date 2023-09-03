package io.github.ryuryu_ymj.notebook.gestures

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.coroutineScope

@Stable
interface CentroidTransformableState {
  /**
   * Call this function to take control of transformations and gain the ability to send transform
   * events via [CentroidTransformScope.transformBy]. All actions that change zoom, pan or rotation
   * values must be performed within a [transform] block (even if they don't call any other methods
   * on this object) in order to guarantee that mutual exclusion is enforced.
   *
   * If [transform] is called from elsewhere with the [transformPriority] higher or equal to ongoing
   * transform, ongoing transform will be canceled.
   */
  suspend fun transform(
      transformPriority: MutatePriority = MutatePriority.Default,
      block: suspend CentroidTransformScope.() -> Unit
  )

  /**
   * Whether this [CentroidTransformableState] is currently transforming by gesture or
   * programmatically or not.
   */
  val isTransformInProgress: Boolean
}

interface CentroidTransformScope {
  /**
   * Attempts to transform by [zoomChange] in relative multiplied value, by [panChange] in pixels
   * and by [rotationChange] in degrees.
   *
   * @param centroid centroid of all the pointers that are down
   * @param panChange panning offset change, in [Offset] pixels
   * @param zoomChange scale factor multiplier change for zoom
   * @param rotationChange change of the rotation in degrees
   */
  fun transformBy(
      centroid: Offset,
      panChange: Offset = Offset.Zero,
      zoomChange: Float = 1f,
      rotationChange: Float = 0f
  )
}

/**
 * Default implementation of [CentroidTransformableState] interface that contains necessary
 * information about the ongoing transformations and provides smooth transformation capabilities.
 *
 * This is the simplest way to set up a [centroidTransformable] modifier. When constructing this
 * [CentroidTransformableState], you must provide a [onTransformation] lambda, which will be invoked
 * whenever pan, zoom or rotation happens (by gesture input or any
 * [CentroidTransformableState.transform] call) with the deltas from the previous event.
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 *   change from the previous event. It's relative scale multiplier for zoom, [Offset] in pixels for
 *   pan and degrees for rotation. Callers should update their state in this lambda.
 */
fun centroidTransformState(
    onTransformation:
        (centroid: Offset, panChange: Offset, zoomChange: Float, rotationChange: Float) -> Unit
): CentroidTransformableState = DefaultCentroidTransformableState(onTransformation)

/**
 * Create and remember default implementation of [CentroidTransformScope] interface that contains
 * necessary information about the ongoing transformations and provides smooth transformation
 * capabilities.
 *
 * This is the simplest way to set up a [centroidTransformable] modifier. When constructing this
 * [CentroidTransformableState], you must provide a [onTransformation] lambda, which will be invoked
 * whenever pan, zoom or rotation happens (by gesture input or any
 * [CentroidTransformableState.transform] call) with the deltas from the previous event.
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 *   change from the previous event. It's relative scale multiplier for zoom, [Offset] in pixels for
 *   pan and degrees for rotation. Callers should update their state in this lambda.
 */
@Composable
fun rememberCentroidTransformState(
    onTransformation:
        (centroid: Offset, panChange: Offset, zoomChange: Float, rotationChange: Float) -> Unit
): CentroidTransformableState {

  val lambdaState = rememberUpdatedState(onTransformation)
  return remember { centroidTransformState { c, p, z, r -> lambdaState.value.invoke(c, p, z, r) } }
}

/**
 * Animate zoom by a ratio of [zoomFactor] over the current size and suspend until its finished.
 *
 * @param zoomFactor ratio over the current size by which to zoom. For example, if [zoomFactor] is
 *   `3f`, zoom will be increased 3 fold from the current value.
 * @param animationSpec [AnimationSpec] to be used for animation
 */
suspend fun CentroidTransformableState.animateZoomBy(
    centroid: Offset,
    zoomFactor: Float,
    animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  require(zoomFactor > 0) { "zoom value should be greater than 0" }
  var previous = 1f
  transform {
    AnimationState(initialValue = previous).animateTo(zoomFactor, animationSpec) {
      val scaleFactor = if (previous == 0f) 1f else this.value / previous
      transformBy(centroid = centroid, zoomChange = scaleFactor)
      previous = this.value
    }
  }
}

/**
 * Animate rotate by a ratio of [degrees] clockwise and suspend until its finished.
 *
 * @param degrees ratio over the current size by which to rotate, in degrees
 * @param animationSpec [AnimationSpec] to be used for animation
 */
suspend fun CentroidTransformableState.animateRotateBy(
    centroid: Offset,
    degrees: Float,
    animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  var previous = 0f
  transform {
    AnimationState(initialValue = previous).animateTo(degrees, animationSpec) {
      val delta = this.value - previous
      transformBy(centroid = centroid, rotationChange = delta)
      previous = this.value
    }
  }
}

/**
 * Animate pan by [offset] Offset in pixels and suspend until its finished
 *
 * @param offset offset to pan, in pixels
 * @param animationSpec [AnimationSpec] to be used for pan animation
 */
suspend fun CentroidTransformableState.animatePanBy(
    offset: Offset,
    animationSpec: AnimationSpec<Offset> = SpringSpec(stiffness = Spring.StiffnessLow)
) {
  var previous = Offset.Zero
  transform {
    AnimationState(typeConverter = Offset.VectorConverter, initialValue = previous).animateTo(
        offset, animationSpec) {
          val delta = this.value - previous
          transformBy(centroid = Offset.Zero, panChange = delta)
          previous = this.value
        }
  }
}

/**
 * Zoom without animation by a ratio of [zoomFactor] over the current size and suspend until it's
 * set.
 *
 * @param zoomFactor ratio over the current size by which to zoom
 */
suspend fun CentroidTransformableState.zoomBy(centroid: Offset, zoomFactor: Float) = transform {
  transformBy(centroid, Offset.Zero, zoomFactor, 0f)
}

/**
 * Rotate without animation by a [degrees] degrees and suspend until it's set.
 *
 * @param degrees degrees by which to rotate
 */
suspend fun CentroidTransformableState.rotateBy(centroid: Offset, degrees: Float) = transform {
  transformBy(centroid, Offset.Zero, 1f, degrees)
}

/**
 * Pan without animation by a [offset] Offset in pixels and suspend until it's set.
 *
 * @param offset offset in pixels by which to pan
 */
suspend fun CentroidTransformableState.panBy(offset: Offset) = transform {
  transformBy(Offset.Zero, offset, 1f, 0f)
}

/**
 * Stop and suspend until any ongoing [CentroidTransformableState.transform] with priority
 * [terminationPriority] or lower is terminated.
 *
 * @param terminationPriority transformation that runs with this priority or lower will be stopped
 */
suspend fun CentroidTransformableState.stopTransformation(
    terminationPriority: MutatePriority = MutatePriority.Default
) {
  this.transform(terminationPriority) {
    // do nothing, just lock the mutex so other scroll actors are cancelled
  }
}

private class DefaultCentroidTransformableState(
    val onTransformation:
        (centroid: Offset, panChange: Offset, zoomChange: Float, rotationChange: Float) -> Unit
) : CentroidTransformableState {

  private val transformScope: CentroidTransformScope =
      object : CentroidTransformScope {
        override fun transformBy(
            centroid: Offset,
            panChange: Offset,
            zoomChange: Float,
            rotationChange: Float
        ) = onTransformation(centroid, panChange, zoomChange, rotationChange)
      }

  private val transformMutex = MutatorMutex()

  private val isTransformingState = mutableStateOf(false)

  override suspend fun transform(
      transformPriority: MutatePriority,
      block: suspend CentroidTransformScope.() -> Unit
  ): Unit = coroutineScope {
    transformMutex.mutateWith(transformScope, transformPriority) {
      isTransformingState.value = true
      try {
        block()
      } finally {
        isTransformingState.value = false
      }
    }
  }

  override val isTransformInProgress: Boolean
    get() = isTransformingState.value
}
