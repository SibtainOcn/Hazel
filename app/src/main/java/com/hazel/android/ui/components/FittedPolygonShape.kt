package com.hazel.android.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.min

/**
 * A polygon drawn by its own bounds rather than its unit square, so a shape that is wider
 * than tall fills the full height it is given. Size the box as [aspect] wide per unit tall.
 */
class FittedPolygonShape(polygon: RoundedPolygon) : Shape {
    private val bounds = polygon.calculateBounds()
    private val path = polygon.toPath().asComposePath()
    private val width = bounds[2] - bounds[0]
    private val height = bounds[3] - bounds[1]

    val aspect: Float = width / height

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val scale = min(size.width / width, size.height / height)
        val matrix = Matrix().apply {
            translate((size.width - width * scale) / 2f, (size.height - height * scale) / 2f)
            scale(scale, scale)
            translate(-bounds[0], -bounds[1])
        }
        return Outline.Generic(Path().apply { addPath(path); transform(matrix) })
    }
}
