package io.github.ryuryu_ymj.notebook

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill

private data class Point(val x: Float, val y: Float, val radius: Float)

class Stroke {
  private val points = mutableListOf<Point>()
  private val path = Path()
  private val color = Color.Blue

  fun begin(x: Float, y: Float, radius: Float) {
    points.add(Point(x, y, radius))
  }

  fun extend(x: Float, y: Float, radius: Float) {
    val p1 = points.last()
    val p2 = Point(x, y, radius)
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

      points.add(p2)
    }
  }

  fun end() {}

  fun DrawScope.drawStroke() {
    drawPath(path, color, style = Fill)
    for (p in points) drawCircle(color, p.radius, Offset(p.x, p.y))
  }
}
