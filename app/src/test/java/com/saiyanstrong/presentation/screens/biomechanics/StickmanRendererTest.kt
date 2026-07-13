package com.saiyanstrong.presentation.screens.biomechanics

import com.saiyanstrong.domain.model.NodeId
import com.saiyanstrong.domain.model.NodePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickmanRendererTest {

    private val allNodesAtCenter = NodeId.entries.map { NodePosition(it, 0.5f, 0.5f) }

    @Test
    fun `jointDotNodes covers every body node except BAR`() {
        val expected = NodeId.entries.filter { it != NodeId.BAR }.toSet()
        assertEquals(expected, StickmanRenderer.jointDotNodes.toSet())
    }

    @Test
    fun `isHeadNode true only for HEAD`() {
        assertTrue(StickmanRenderer.isHeadNode(NodeId.HEAD))
        assertTrue(NodeId.entries.filter { it != NodeId.HEAD }.none(StickmanRenderer::isHeadNode))
    }

    @Test
    fun `limb segment groups cover the exact spec segment pairs, no more no less`() {
        val expectedPairs = setOf(
            NodeId.NECK_BASE to NodeId.HEAD,
            NodeId.NECK_BASE to NodeId.L_SHOULDER, NodeId.NECK_BASE to NodeId.R_SHOULDER,
            NodeId.NECK_BASE to NodeId.HIP_CENTER,
            NodeId.L_SHOULDER to NodeId.L_ELBOW, NodeId.L_ELBOW to NodeId.L_WRIST, NodeId.L_WRIST to NodeId.BAR,
            NodeId.R_SHOULDER to NodeId.R_ELBOW, NodeId.R_ELBOW to NodeId.R_WRIST, NodeId.R_WRIST to NodeId.BAR,
            NodeId.HIP_CENTER to NodeId.L_HIP, NodeId.HIP_CENTER to NodeId.R_HIP,
            NodeId.L_HIP to NodeId.L_KNEE, NodeId.L_KNEE to NodeId.L_ANKLE, NodeId.L_ANKLE to NodeId.L_TOE,
            NodeId.R_HIP to NodeId.R_KNEE, NodeId.R_KNEE to NodeId.R_ANKLE, NodeId.R_ANKLE to NodeId.R_TOE
        )
        val actualPairs = StickmanRenderer.limbSegmentGroupsInOrder.flatten().toSet()
        assertEquals(expectedPairs, actualPairs)
    }

    @Test
    fun `draw order groups are right leg, left leg, torso, right arm, left arm, head-neck`() {
        val groups = StickmanRenderer.limbSegmentGroupsInOrder
        assertEquals(6, groups.size)
        assertTrue(groups[0].all { it.first == NodeId.HIP_CENTER || it.first.name.startsWith("R_") })
        assertTrue(groups[1].all { it.first == NodeId.HIP_CENTER || it.first.name.startsWith("L_") })
        assertEquals(listOf(NodeId.NECK_BASE to NodeId.HIP_CENTER), groups[2])
        assertEquals(NodeId.NECK_BASE to NodeId.HEAD, groups[5].single())
    }

    @Test
    fun `findNode returns the matching node or null`() {
        val nodes = listOf(NodePosition(NodeId.HEAD, 0.1f, 0.2f))
        assertEquals(0.2f, StickmanRenderer.findNode(nodes, NodeId.HEAD)?.y)
        assertNull(StickmanRenderer.findNode(nodes, NodeId.BAR))
    }

    @Test
    fun `floorYFraction sits just below the lowest ankle or toe present`() {
        val nodes = listOf(
            NodePosition(NodeId.L_ANKLE, 0.4f, 0.80f),
            NodePosition(NodeId.R_ANKLE, 0.6f, 0.85f),
            NodePosition(NodeId.L_TOE, 0.45f, 0.90f),
            NodePosition(NodeId.R_TOE, 0.65f, 0.88f)
        )
        assertEquals(0.92f, StickmanRenderer.floorYFraction(nodes, marginFraction = 0.02f), 1e-4f)
    }

    @Test
    fun `floorYFraction clamps to 1 rather than overflowing the canvas`() {
        val nodes = listOf(NodePosition(NodeId.L_TOE, 0.5f, 0.995f))
        assertEquals(1f, StickmanRenderer.floorYFraction(nodes, marginFraction = 0.02f), 1e-4f)
    }

    @Test
    fun `floorYFraction falls back to a fixed margin when no foot node is present`() {
        assertEquals(0.98f, StickmanRenderer.floorYFraction(allNodesAtCenter.filter { it.id != NodeId.L_ANKLE && it.id != NodeId.R_ANKLE && it.id != NodeId.L_TOE && it.id != NodeId.R_TOE }, marginFraction = 0.02f), 1e-4f)
    }
}
