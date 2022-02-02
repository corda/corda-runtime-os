package net.corda.flow.manager.impl.acceptance

import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowFiber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class MockFlowFiberTest {

    private companion object {
        val emptyBytes: ByteBuffer = ByteBuffer.wrap(byteArrayOf(0))
    }

    private val fiber = MockFlowFiber()

    @Test
    fun `queue and dequeue suspensions`() {
        fiber.queueSuspension(FlowIORequest.ForceCheckpoint)
        fiber.queueSuspension(FlowIORequest.WaitForSessionConfirmations)
        fiber.queueSuspension(FlowIORequest.FlowFinished(Unit))

        assertEquals(FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.WaitForSessionConfirmations), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowFinished(Unit), fiber.dequeueSuspension())
    }

    @Test
    fun `repeat queue and dequeue suspensions`() {
        fiber.repeatSuspension(FlowIORequest.ForceCheckpoint, 1)
        fiber.repeatSuspension(FlowIORequest.WaitForSessionConfirmations, 2)
        fiber.repeatSuspension(FlowIORequest.FlowFinished(Unit), 3)

        assertEquals(FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.ForceCheckpoint), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.WaitForSessionConfirmations), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowSuspended(emptyBytes, FlowIORequest.WaitForSessionConfirmations), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowFinished(Unit), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowFinished(Unit), fiber.dequeueSuspension())
        assertEquals(FlowIORequest.FlowFinished(Unit), fiber.dequeueSuspension())
    }

    @Test
    fun `cannot dequeue when there are no queued suspensions`() {
        assertThrows<IllegalStateException> { MockFlowFiber().dequeueSuspension() }
    }
}