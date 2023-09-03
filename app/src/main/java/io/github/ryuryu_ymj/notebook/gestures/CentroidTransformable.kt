package io.github.ryuryu_ymj.notebook.gestures

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import io.github.ryuryu_ymj.notebook.gestures.CentroidTransformEvent.*
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

fun Modifier.centroidTransformable(
    state: CentroidTransformableState,
    enabled: Boolean = true,
    canTransform: (PointerInputChange) -> Boolean = { true },
    lockRotationOnZoomPan: Boolean = false,
    startTransformImmediately: () -> Boolean,
    onTransformStopped: suspend CoroutineScope.(velocity: Velocity) -> Unit = {}
) =
    composed(
        factory = {
          val updatePanZoomLock = rememberUpdatedState(lockRotationOnZoomPan)
          val canTransformState = rememberUpdatedState(canTransform)
          val channel = remember { Channel<CentroidTransformEvent>(capacity = Channel.UNLIMITED) }
          val startImmediatelyState = rememberUpdatedState(startTransformImmediately)
          if (enabled) {
            LaunchedEffect(state) {
              while (isActive) {
                var event = channel.receive()
                if (event !is TransformStarted) continue
                try {
                  state.transform(MutatePriority.UserInput) {
                    while (event !is TransformStopped && event !is TransformCancelled) {
                      (event as? TransformDelta)?.let {
                        transformBy(it.centroid, it.panChange, it.zoomChange, it.rotationChange)
                      }
                      event = channel.receive()
                    }
                  }
                  if (event is TransformStopped) {
                    onTransformStopped.invoke(this, (event as TransformStopped).velocity)
                  }
                } catch (c: CancellationException) {
                  onTransformStopped.invoke(this, Velocity.Zero)
                }
              }
            }
          }
          val block: suspend PointerInputScope.() -> Unit = remember {
            {
              coroutineScope {
                awaitEachGesture {
                  val velocityTracker = DeltaVelocityTracker()
                  var isTransformSuccessful = false
                  try {
                    isTransformSuccessful =
                        awaitTransform(
                            canTransformState,
                            startImmediatelyState,
                            updatePanZoomLock,
                            velocityTracker,
                            channel)
                  } catch (exception: CancellationException) {
                    isTransformSuccessful = false
                    if (!isActive) throw exception
                  } finally {
                    val event =
                        if (isTransformSuccessful) {
                          val velocity = velocityTracker.calculateVelocity()
                          TransformStopped(velocity)
                        } else {
                          TransformCancelled
                        }
                    channel.trySend(event)
                  }
                }
              }
            }
          }
          if (enabled) Modifier.pointerInput(Unit, block) else Modifier
        },
        inspectorInfo =
            debugInspectorInfo {
              name = "transformable"
              properties["state"] = state
              properties["enabled"] = enabled
              properties["lockRotationOnZoomPan"] = lockRotationOnZoomPan
            })

private sealed class CentroidTransformEvent {
  object TransformStarted : CentroidTransformEvent()
  object TransformCancelled : CentroidTransformEvent()
  class TransformStopped(val velocity: Velocity) : CentroidTransformEvent()
  class TransformDelta(
      val centroid: Offset,
      val panChange: Offset = Offset.Zero,
      val zoomChange: Float = 1f,
      val rotationChange: Float = 0f
  ) : CentroidTransformEvent()
}

@OptIn(ExperimentalComposeUiApi::class)
private suspend fun AwaitPointerEventScope.awaitTransform(
    canTransform: State<(PointerInputChange) -> Boolean>,
    startTransformImmediately: State<() -> Boolean>,
    panZoomLock: State<Boolean>,
    velocityTracker: DeltaVelocityTracker,
    channel: Channel<CentroidTransformEvent>
): Boolean {
  var rotation = 0f
  var zoom = 1f
  var pan = Offset.Zero
  var pastTouchSlop = false
  val touchSlop = viewConfiguration.touchSlop
  var lockedToPanZoom = false

  val initialDown = awaitFirstDown(requireUnconsumed = false)
  if (!canTransform.value.invoke(initialDown)) {
    return false
  } else if (startTransformImmediately.value.invoke()) {
    pastTouchSlop = true
    channel.trySend(TransformStarted)
    channel.trySend(TransformDelta(Offset.Zero))
  }
  velocityTracker.resetTracking()
  initialDown.consume()

  do {
    val event = awaitPointerEvent()
    val canceled = event.changes.fastAny { it.isConsumed }
    if (!canceled) {
      velocityTracker.addMultiPointerInputChanges(event.changes)
      val zoomChange = event.calculateZoom()
      val rotationChange = event.calculateRotation()
      val panChange = event.calculatePan()

      if (!pastTouchSlop) {
        zoom *= zoomChange
        rotation += rotationChange
        pan += panChange

        val centroidSize = event.calculateCentroidSize(useCurrent = false)
        val zoomMotion = abs(1 - zoom) * centroidSize
        val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
        val panMotion = pan.getDistance()

        if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
          pastTouchSlop = true
          lockedToPanZoom = panZoomLock.value && rotationMotion < touchSlop
          channel.trySend(TransformStarted)
        }
      }

      if (pastTouchSlop) {
        val centroid = event.calculateCentroid(useCurrent = true)
        val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
        if (effectiveRotation != 0f || zoomChange != 1f || panChange != Offset.Zero) {
          channel.trySend(TransformDelta(centroid, panChange, zoomChange, effectiveRotation))
        }
        event.changes.fastForEach {
          if (it.positionChanged()) {
            it.consume()
          }
        }
      }
    }
  } while (!canceled && event.changes.fastAny { it.pressed })
  return pastTouchSlop
}
