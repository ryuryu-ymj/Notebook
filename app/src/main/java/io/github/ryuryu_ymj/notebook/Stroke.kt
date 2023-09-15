package io.github.ryuryu_ymj.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import io.github.ryuryu_ymj.notebook.smoother.OnPointsAdd

private data class Point(val x: Float, val y: Float, val radius: Float)

@Composable
fun Stroke(modifier: Modifier = Modifier) {
  val points = remember { mutableStateListOf<Point>() }
  val pen = remember { Pen() }
  val path =
      remember(points.size) {
        val path = Path()
        for ((p1, p2) in points.windowed(2)) {
          var dx = p2.x - p1.x
          var dy = p2.y - p1.y
          val dr = kotlin.math.hypot(dx, dy)
          dx /= dr
          dy /= dr
          if (dx.isFinite() && dy.isFinite()) {
            path.moveTo(p1.x + dy * p1.radius, p1.y - dx * p1.radius)
            path.lineTo(p1.x - dy * p1.radius, p1.y + dx * p1.radius)
            path.lineTo(p2.x - dy * p2.radius, p2.y + dx * p2.radius)
            path.lineTo(p2.x + dy * p2.radius, p2.y - dx * p2.radius)
            path.close()
          }
        }
        path
      }
  val color = Color.Blue

  Canvas(
      modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
          // touch down
          val down = awaitFirstDown()
          if (down.type == PointerType.Stylus) {
            val onPointsAdd: OnPointsAdd = { x, y, pressure ->
              val radius = pen.pressureToRadius(pressure)
              points.add(Point(x, y, radius))
              //              Log.d(TAG, "$x, $y")
            }
            pen.smoother.beginTouch(
                down.position.x, down.position.y, down.pressure, down.uptimeMillis, onPointsAdd)

            do {
              // touch move
              val event = awaitPointerEvent()
              @OptIn(ExperimentalComposeUiApi::class)
              event.changes.firstOrNull()?.let { change ->
                change.consume()
                change.historical.forEach {
                  pen.smoother.moveTouch(
                      it.position.x, it.position.y, change.pressure, it.uptimeMillis, onPointsAdd)
                }
                pen.smoother.moveTouch(
                    change.position.x,
                    change.position.y,
                    change.pressure,
                    change.uptimeMillis,
                    onPointsAdd)
              }
            } while (event.changes.fastAny { it.pressed })

            // touch up
            pen.smoother.endTouch(onPointsAdd)
          }
        }
      }) {
        drawPath(path, color, style = Fill)
        for (p in points) drawCircle(color, p.radius, Offset(p.x, p.y))
      }
}
