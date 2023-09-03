package io.github.ryuryu_ymj.notebook

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import io.github.ryuryu_ymj.notebook.gestures.flingTransformable
import io.github.ryuryu_ymj.notebook.gestures.rememberCentroidTransformState

@Composable
fun Viewer(modifier: Modifier = Modifier) {
  var contentFrame by remember { mutableStateOf(Rect(Offset.Zero, Size(210f, 297f))) }
  //  var viewport by remember { mutableStateOf(Rect(Offset.Zero, Size.Unspecified)) }
  var viewportOffset by remember { mutableStateOf(Offset.Zero) }
  var viewportScale by remember { mutableFloatStateOf(1f) }
  var layoutSize by remember { mutableStateOf(IntSize.Zero) }

  fun contentScale() = layoutSize.width / contentFrame.width

  val state = rememberCentroidTransformState { centroid, pan, zoom, _ ->
    val oldScale = viewportScale
    viewportScale = (viewportScale * zoom).coerceIn(0.5f, 10f)

    val contentPan = pan / contentScale()
    val contentCentroid = centroid / contentScale()
    val newOffset =
        viewportOffset - contentPan / oldScale + contentCentroid / oldScale -
            contentCentroid / viewportScale
    val viewportWidth = contentFrame.width / viewportScale
    val viewportHeight = viewportWidth / layoutSize.width * layoutSize.height
    val viewportX =
        if (viewportWidth < contentFrame.width) {
          newOffset.x.coerceIn(contentFrame.left, contentFrame.right - viewportWidth)
        } else {
          contentFrame.center.x - viewportWidth / 2
        }
    val viewportY =
        if (viewportHeight < contentFrame.height) {
          newOffset.y.coerceIn(contentFrame.top, contentFrame.bottom - viewportHeight)
        } else {
          contentFrame.center.y - viewportHeight / 2
        }
    viewportOffset = Offset(viewportX, viewportY)
  }

  SideEffect { Log.d(TAG, "Viewer is (re)composed.") }

  Canvas(
      modifier
          .onGloballyPositioned { layoutSize = it.size }
          .clipToBounds()
          .flingTransformable(state = state)
          .graphicsLayer {
            translationX = -viewportOffset.x * viewportScale * contentScale()
            translationY = -viewportOffset.y * viewportScale * contentScale()
            scaleX = viewportScale * contentScale()
            scaleY = viewportScale * contentScale()
            transformOrigin = TransformOrigin(0f, 0f)
          }) {
        Log.d(TAG, "Viewer is drawn. size: $size")
        drawRect(Color.LightGray, contentFrame.topLeft, contentFrame.size)
        for (x in 0 until contentFrame.width.toInt() step 10) {
          drawLine(
              Color.Black, start = Offset(x.toFloat(), 0f), end = Offset(x.toFloat(), size.height))
        }
        for (y in 0 until contentFrame.height.toInt() step 10) {
          drawLine(
              Color.Black, start = Offset(0f, y.toFloat()), end = Offset(size.width, y.toFloat()))
        }
      }
}
