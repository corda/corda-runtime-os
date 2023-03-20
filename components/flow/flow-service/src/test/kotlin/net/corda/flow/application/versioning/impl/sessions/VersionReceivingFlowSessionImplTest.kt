package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.v5.application.serialization.SerializationService
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

    private companion object {
        const val PAYLOAD = "payload"
        const val INITIAL_PAYLOAD = "initial payload"
        val SERIALIZED_INITIAL_PAYLOAD = byteArrayOf(1, 1, 1, 1)
    }

    private val delegate = mock<FlowSessionInternal>()
    private val serializationService = mock<SerializationService>()

    private val session = VersionReceivingFlowSessionImpl(SERIALIZED_INITIAL_PAYLOAD, delegate, serializationService)

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(SERIALIZED_INITIAL_PAYLOAD, Any::class.java)).thenReturn(INITIAL_PAYLOAD)
        whenever(delegate.receive(Any::class.java)).thenReturn(PAYLOAD)
        whenever(delegate.sendAndReceive(eq(Any::class.java), any())).thenReturn(PAYLOAD)
    }

    @Test
    fun `extracts the received payload from the initial payload the first time receive is called`() {
        assertThat(session.receive(Any::class.java)).isEqualTo(INITIAL_PAYLOAD)
        verify(delegate, never()).receive(Any::class.java)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Any::class.java)
    }

    @Test
    fun `extracts the received payload from the initial payload and does a separate send the first time sendAndReceive is called`() {
        assertThat(session.sendAndReceive(Any::class.java, PAYLOAD)).isEqualTo(INITIAL_PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Any::class.java), any())
        verify(delegate).send(PAYLOAD)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Any::class.java)
    }

    @Test
    fun `extracts the received payload from the initial payload the first time getInitialPayloadIfNotAlreadyReceived is called`() {
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).isEqualTo(INITIAL_PAYLOAD)
        verify(serializationService).deserialize(SERIALIZED_INITIAL_PAYLOAD, Any::class.java)
    }

    @Test
    fun `does a normal receive after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        assertThat(session.receive(Any::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does a normal receive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Any::class.java), any())
        assertThat(session.receive(Any::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does a normal receive after getInitialPayloadIfNotAlreadyReceived is called`() {
        session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)
        assertThat(session.receive(Any::class.java)).isEqualTo(PAYLOAD)
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does a normal sendAndReceive after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        assertThat(session.sendAndReceive(Any::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Any::class.java, PAYLOAD)
    }

    @Test
    fun `does a normal sendAndReceive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Any::class.java), any())
        assertThat(session.sendAndReceive(Any::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Any::class.java, PAYLOAD)
    }

    @Test
    fun `does a normal sendAndReceive after getInitialPayloadIfNotAlreadyReceived is called`() {
        session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)
        assertThat(session.sendAndReceive(Any::class.java, PAYLOAD)).isEqualTo(PAYLOAD)
        verify(delegate).sendAndReceive(Any::class.java, PAYLOAD)
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after receive is called`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).isNull()
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after sendAndReceive is called`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(eq(Any::class.java), any())
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).isNull()
    }

    @Test
    fun `returns null when calling getInitialPayloadIfNotAlreadyReceived after getInitialPayloadIfNotAlreadyReceived is called`() {
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).isEqualTo(INITIAL_PAYLOAD)
        assertThat(session.getInitialPayloadIfNotAlreadyReceived(Any::class.java)).isNull()
    }
}