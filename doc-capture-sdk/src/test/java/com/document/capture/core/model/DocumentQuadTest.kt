package com.document.capture.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentQuadTest {

    @Test
    fun testConvexQuad() {
        val quad = DocumentQuad(
            topLeft = PointF(10f, 10f),
            topRight = PointF(100f, 10f),
            bottomRight = PointF(100f, 60f),
            bottomLeft = PointF(10f, 60f)
        )

        assertTrue(quad.isConvex())
        assertEquals(90f * 50f, quad.getArea(), 0.1f)
        assertEquals(90f, quad.getWidth(), 0.1f)
        assertEquals(50f, quad.getHeight(), 0.1f)
        assertEquals(1.8f, quad.getAspectRatio(), 0.1f)
    }

    @Test
    fun testUnorderedCornerSorting() {
        // Randomly ordered 4 points
        val points = listOf(
            PointF(100f, 60f), // BR
            PointF(10f, 10f),  // TL
            PointF(10f, 60f),  // BL
            PointF(100f, 10f)  // TR
        )

        val ordered = DocumentQuad.fromUnordered(points)
        assertEquals(PointF(10f, 10f), ordered.topLeft)
        assertEquals(PointF(100f, 10f), ordered.topRight)
        assertEquals(PointF(100f, 60f), ordered.bottomRight)
        assertEquals(PointF(10f, 60f), ordered.bottomLeft)
    }

    @Test
    fun testNonConvexQuad() {
        // Concave quad where BR folds inward past the center
        val concave = DocumentQuad(
            topLeft = PointF(0f, 0f),
            topRight = PointF(100f, 0f),
            bottomRight = PointF(20f, 20f), // inward fold
            bottomLeft = PointF(0f, 100f)
        )
        assertFalse(concave.isConvex())
    }
}
