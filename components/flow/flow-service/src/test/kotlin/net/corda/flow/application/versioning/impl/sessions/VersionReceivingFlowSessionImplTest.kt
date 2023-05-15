package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VersionReceivingFlowSessionImplTest {

    @CordaSerializable
    data class Payload(val message: String)

    private companion object {
        val PAYLOAD = Payload("payload")
        val INITIAL_PAYLOAD = Payload("initial payload")
        val SERIALIZED_INITIAL_PAYLOAD = byteArrayOf(1, 1, 1, 1)
    }

    private val delegate = mock<FlowSessionInternal>()
    private val serializationService = mock<SerializationService>()

    private val session = VersionReceivingFlowSessionImpl(SERIALIZED_INITIAL_PAYLOAD, delegate, serializationService)

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(SERIALIZED_INITIAL_PAYLOAD, Payload::class.java)).thenReturn(INITIAL_PAYLOAD)
        whenever(delegate.receive(Payload::class.java)).thenReturn(PAYLOAD)
        whenever(delegate.sendAndReceive(eq(Payload::class.java), any())).thenReturn(PAYLOAD)
    }

    @Test
    fun `extracts the received payload from the initial payload the first time receive is called`() {
        assertThat(session.receive(Payload::class.java)).isEqualTo(INITIAL_PAYLOAD)
        verify(delegate, never()).receive(Payload::class.java)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Payload::class.java)
    }

    @Test
    fun `extracts the received payload from the initial payload and does a separate send the first time sendAndReceive is called`() {
        assertThat(session.sendAndReceive(Payload::class.java, PAYLOAD)).isEqualTo(INITIAL_PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Payload::class.java), any())
        verify(delegate).send(PAYLOAD)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Payload::class.java)
    }

    @Test
    fun `extracts the received payload from the initial payload the first time getInitialPayloadIfNotAlreadyReceived is called`() {
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)).isEqualTo(INITIAL_PAYLOAD)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Payload::class.java)
    }

    @Test
    fun `does a normal receive after the session has done a receive`() {
        session.receive(Payload::class.java)
        verify(delegate, never()).receive(Payload::class.java)
        assertThat(session.receive(Payload::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Payload::class.java)
    }

    @Test
    fun `does a normal receive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Payload::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Payload::class.java), any())
        assertThat(session.receive(Payload::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Payload::class.java)
    }

    @Test
    fun `does a normal receive after getInitialPayloadIfNotAlreadyReceived is called`() {
        session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)
        assertThat(session.receive(Payload::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Payload::class.java)
    }

    @Test
    fun `does a normal sendAndReceive after the session has done a receive`() {
        session.receive(Payload::class.java)
        verify(delegate, never()).receive(Payload::class.java)
        assertThat(session.sendAndReceive(Payload::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Payload::class.java, PAYLOAD)
    }

    @Test
    fun `does a normal sendAndReceive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Payload::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Payload::class.java), any())
        assertThat(session.sendAndReceive(Payload::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Payload::class.java, PAYLOAD)
    }

    @Test
    fun `does a normal sendAndReceive after getInitialPayloadIfNotAlreadyReceived is called`() {
        session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)
        assertThat(session.sendAndReceive(Payload::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Payload::class.java, PAYLOAD)
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after receive is called`() {
        session.receive(Payload::class.java)
        verify(delegate, never()).receive(Payload::class.java)
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)).isNull()
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after sendAndReceive is called`() {
        session.sendAndReceive(Payload::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Payload::class.java), any())
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)).isNull()
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after getInitialPayloadIfNotAlreadyReceived is called`() {
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)).isEqualTo(INITIAL_PAYLOAD)
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Payload::class.java)).isNull()
    }
}