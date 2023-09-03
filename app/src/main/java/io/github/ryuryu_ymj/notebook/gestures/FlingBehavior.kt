package io.github.ryuryu_ymj.notebook.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity

interface FlingBehavior {
  suspend fun CentroidTransformScope.performFling(
      centroid: Offset,
      panVelocity: Velocity,
      zoomVelocity: Float,
      rotationVelocity: Float
  )
}
