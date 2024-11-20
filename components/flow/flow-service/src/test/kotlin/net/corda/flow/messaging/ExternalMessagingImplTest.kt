package net.corda.flow.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.flow.application.messaging.ExternalMessagingImpl
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalMessagingImplTest {

    private val flowFiberService = MockFlowFiberService()
    private val mockSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val mockSerializer = mock<CordaAvroSerializer<Any>>()
    private lateinit var target: ExternalMessagingImpl

    @BeforeEach
    fun setup() {
        whenever(mockSerializationFactory.createAvroSerializer<Any>()).thenReturn(mockSerializer)
        whenever(mockSerializer.serialize(anyOrNull())).thenReturn(byteArrayOf(1, 2, 3))
        whenever(flowFiberService.flowCheckpoint.maxMessageSize).thenReturn(100000)
        target = ExternalMessagingImpl(flowFiberService, { "random_id" }, mockSerializationFactory)
    }

    @Test
    fun `send channel and message issues IO request`() {
        target.send("ch1", "msg1")

        val expectedIoRequest = FlowIORequest.SendExternalMessage(
            "ch1",
            "random_id",
            "msg1"
        )
        verify(flowFiberService.flowFiber).suspend(expectedIoRequest)
    }

    @Test
    fun `send payload exceeds max message size`() {
        whenever(flowFiberService.flowCheckpoint.maxMessageSize).thenReturn(1)
        target = ExternalMessagingImpl(flowFiberService, { "random_id" }, mockSerializationFactory)

        assertThrows<CordaRuntimeException> {  target.send("ch1", "msg1") }
    }

    @Test
    fun `send channel, message id and message issues IO request`() {
        target.send("ch1", "id2", "msg1")

        val expectedIoRequest = FlowIORequest.SendExternalMessage("ch1", "id2", "msg1")
        verify(flowFiberService.flowFiber).suspend(expectedIoRequest)
    }
}
