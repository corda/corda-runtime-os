package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.impl.AgreedVersion
import net.corda.flow.application.versioning.impl.AgreedVersionAndPayload
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VersionSendingFlowSessionImplTest {

    private companion object {
        const val PAYLOAD = "payload"
        const val ANOTHER_PAYLOAD = "another payload"
        const val VERSION = 1
        val ADDITIONAL_CONTEXT = linkedMapOf<String, Any>("key" to "value")
        val SERIALIZED_PAYLOAD = byteArrayOf(1, 1, 1, 1)
        val SERIALIZED_ANOTHER_PAYLOAD = byteArrayOf(2, 2, 2, 2)
        val SERIALIZED_VERSION_AND_PAYLOAD = byteArrayOf(3, 3, 3, 3)
    }

    private val delegate = mock<FlowSessionInternal>()
    private val serializationService = mock<SerializationService>()

    private val session = VersionSendingFlowSessionImpl(VERSION, ADDITIONAL_CONTEXT, delegate, serializationService)

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.serialize(PAYLOAD)).thenReturn(SerializedBytes(SERIALIZED_PAYLOAD))
        whenever(serializationService.serialize(ANOTHER_PAYLOAD)).thenReturn(SerializedBytes(SERIALIZED_ANOTHER_PAYLOAD))
        whenever(serializationService.serialize(any<AgreedVersionAndPayload>())).thenReturn(SerializedBytes(SERIALIZED_VERSION_AND_PAYLOAD))
        whenever(delegate.sendAndReceive(eq(Any::class.java), any())).thenReturn(PAYLOAD)
        whenever(delegate.receive(Any::class.java)).thenReturn(PAYLOAD)
    }

    @Test
    fun `sends versioning information along with the payload the first time a send is called`() {
        session.send(PAYLOAD)
        verify(delegate).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_PAYLOAD))
        verify(delegate, never()).send(PAYLOAD)
    }

    @Test
    fun `sends versioning information along with the payload the first time a sendAndReceive is called`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_PAYLOAD)
        )
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
    }

    @Test
    fun `sends versioning information separately the first time a receive is called`() {
        session.receive(Any::class.java)
        verify(delegate).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), serializedPayload = null)
        )
        verify(delegate, never()).receive(Any::class.java)
    }

    @Test
    fun `sends versioning information separately the first time a close is called`() {
        session.close()
        delegate.inOrder {
            verify().send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), serializedPayload = null))
            verify().close()
        }
    }

    @Test
    fun `serializes versioning information with the passed in payload the first time getPayloadToSend is called`() {
        assertThat(session.getPayloadToSend(SERIALIZED_PAYLOAD)).isEqualTo(SERIALIZED_VERSION_AND_PAYLOAD)
        verify(serializationService).serialize(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_PAYLOAD))
    }

    @Test
    fun `serializes versioning information with no payload the first time getVersioningPayloadToSend is called`() {
        assertThat(session.getVersioningPayloadToSend()).isEqualTo(SERIALIZED_VERSION_AND_PAYLOAD)
        verify(serializationService).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                serializedPayload = null
            )
        )
    }

    @Test
    fun `does not send versioning information when calling send after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling send after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling send after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling send after close is called`() {
        session.close()
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling send after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling send after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.send(ANOTHER_PAYLOAD)
        verify(delegate, never()).send(AgreedVersionAndPayload(AgreedVersion(VERSION, ADDITIONAL_CONTEXT), SERIALIZED_ANOTHER_PAYLOAD))
        verify(delegate).send(ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after close is called`() {
        session.close()
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling sendAndReceive after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
        verify(delegate, never()).sendAndReceive(
            Any::class.java,
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
        verify(delegate).sendAndReceive(Any::class.java, ANOTHER_PAYLOAD)
    }

    @Test
    fun `does not send versioning information when calling receive after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        verify(delegate).send(any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(delegate).send(any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling receive after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling receive after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling receive after close is called`() {
        session.close()
        verify(delegate).send(any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(delegate).send(any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling receive after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling receive after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.receive(Any::class.java)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        verify(delegate).receive(Any::class.java)
    }

    @Test
    fun `does not send versioning information when calling close after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        verify(delegate).send(any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate).send(any<AgreedVersionAndPayload>())
        verify(delegate).close()
    }

    @Test
    fun `does not send versioning information when calling close after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate, never()).send(any<AgreedVersionAndPayload>())
        verify(delegate).close()
    }

    @Test
    fun `does not send versioning information when calling close after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        verify(delegate).sendAndReceive(eq(Any::class.java), any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate, never()).send(any<AgreedVersionAndPayload>())
        verify(delegate).close()
    }

    @Test
    fun `does not send versioning information when calling close after the session has done a close`() {
        session.close()
        verify(delegate).close()
        verify(delegate).send(any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate).send(any<AgreedVersionAndPayload>())
        verify(delegate, times(2)).close()
    }

    @Test
    fun `does not send versioning information when calling close after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate, never()).send(any<AgreedVersionAndPayload>())
        verify(delegate).close()
    }

    @Test
    fun `does not send versioning information when calling close after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        session.close()
        verify(delegate, never()).send(any<AgreedVersionAndPayload>())
        verify(delegate).close()
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        assertThat(session.getPayloadToSend(SERIALIZED_PAYLOAD)).isEqualTo(SERIALIZED_PAYLOAD)
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        assertThat(session.getPayloadToSend(SERIALIZED_PAYLOAD)).isEqualTo(SERIALIZED_PAYLOAD)
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        assertThat(session.getPayloadToSend(SERIALIZED_PAYLOAD)).isEqualTo(SERIALIZED_PAYLOAD)
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after close is called`() {
        session.close()
        assertThat(session.getPayloadToSend(SERIALIZED_PAYLOAD)).isEqualTo(SERIALIZED_PAYLOAD)
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        assertThat(session.getPayloadToSend(SERIALIZED_ANOTHER_PAYLOAD)).isEqualTo(SERIALIZED_ANOTHER_PAYLOAD)
        verify(serializationService, never()).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
    }

    @Test
    fun `returns the passed in payload when calling getPayloadToSend after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        assertThat(session.getPayloadToSend(SERIALIZED_ANOTHER_PAYLOAD)).isEqualTo(SERIALIZED_ANOTHER_PAYLOAD)
        verify(serializationService, never()).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                SERIALIZED_ANOTHER_PAYLOAD
            )
        )
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after the session has done a send`() {
        session.send(PAYLOAD)
        verify(delegate, never()).send(PAYLOAD)
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after the session has done a sendAndReceive`() {
        session.sendAndReceive(Any::class.java, PAYLOAD)
        verify(delegate, never()).sendAndReceive(Any::class.java, PAYLOAD)
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after the session has done a receive`() {
        session.receive(Any::class.java)
        verify(delegate, never()).receive(Any::class.java)
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after close is called`() {
        session.close()
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService, never()).serialize(any<AgreedVersionAndPayload>())
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after getPayloadToSend is called`() {
        session.getPayloadToSend(SERIALIZED_PAYLOAD)
        verify(serializationService).serialize(any<AgreedVersionAndPayload>())
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService, never()).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                serializedPayload = null
            )
        )
    }

    @Test
    fun `returns null when calling getVersioningPayloadToSend after getVersioningPayloadToSend is called`() {
        session.getVersioningPayloadToSend()
        verify(serializationService).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                serializedPayload = null
            )
        )
        assertThat(session.getVersioningPayloadToSend()).isNull()
        verify(serializationService).serialize(
            AgreedVersionAndPayload(
                AgreedVersion(VERSION, ADDITIONAL_CONTEXT),
                serializedPayload = null
            )
        )
    }
}