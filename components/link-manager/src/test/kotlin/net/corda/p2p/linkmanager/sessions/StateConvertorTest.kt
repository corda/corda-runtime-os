package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.libs.statemanager.api.State
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.CheckRevocation
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.Security
import net.corda.data.p2p.state.SessionState as AvroSessionState

class StateConvertorTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `toCordaSessionState returns the correct data`() {
        val stateValue = byteArrayOf(1, 2, 3)
        val encryptedData = byteArrayOf(5)
        val decrtptedData = byteArrayOf(10)
        val linkOutMessage = mock<LinkOutMessage>()
        val avroSessionState = mock<AvroSessionState> {
            on { encryptedSessionData } doReturn ByteBuffer.wrap(encryptedData)
            on { message } doReturn linkOutMessage
        }
        val session = Session(
            "sessionId",
            2300,
            AuthenticatedSessionDetails(
                SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
            ),
        )
        val schemaRegistry = mock<AvroSchemaRegistry> {
            on {
                deserialize(
                    ByteBuffer.wrap(stateValue),
                    AvroSessionState::class.java,
                    null,
                )
            } doReturn avroSessionState
            on { getClassType(ByteBuffer.wrap(decrtptedData)) } doReturn Session::class.java
            on {
                deserialize(
                    ByteBuffer.wrap(decrtptedData),
                    Session::class.java,
                    null,
                )
            } doReturn session
        }
        val sessionEncryptionOpsClient = mock<SessionEncryptionOpsClient> {
            on { decryptSessionData(encryptedData) } doReturn decrtptedData
        }
        val state = mock<State> {
            on { value } doReturn stateValue
        }

        val convertor = StateConvertor(
            schemaRegistry,
            sessionEncryptionOpsClient,
        )
        val checkRevocation = mock<CheckRevocation>()

        val result = convertor.toCordaSessionState(
            state,
            checkRevocation,
        )

        assertThat(result?.message).isEqualTo(linkOutMessage)
        assertThat(result?.sessionData).isInstanceOf(AuthenticatedSession::class.java)
    }

    @Test
    fun `toCordaSessionState returns null on failure to decrypt session data`() {
        val avroSessionState = mock<AvroSessionState> {
            on { encryptedSessionData } doReturn ByteBuffer.wrap(byteArrayOf(0))
        }
        val schemaRegistry = mock<AvroSchemaRegistry> {
            on {
                deserialize(
                    any(),
                    eq(AvroSessionState::class.java),
                    eq(null),
                )
            } doReturn avroSessionState
        }
        val sessionEncryptionOpsClient = mock<SessionEncryptionOpsClient> {
            on { decryptSessionData(any(), eq(null)) } doThrow CryptoException("error")
        }
        val convertor = StateConvertor(
            schemaRegistry,
            sessionEncryptionOpsClient,
        )
        val mockState = mock<State> {
            on { value } doReturn byteArrayOf(0)
        }

        val result = convertor.toCordaSessionState(
            mockState,
            mock(),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `toStateByteArray return the correct data`() {
        val linkOutMessage = mock<LinkOutMessage>()
        val encryptedSessionData = ByteBuffer.wrap(byteArrayOf(1))
        val avroState = AvroSessionState(
            linkOutMessage,
            encryptedSessionData,
        )
        val expectedBytes = byteArrayOf(4)
        val schemaRegistry = mock<AvroSchemaRegistry> {
            on { serialize(avroState) } doReturn ByteBuffer.wrap(expectedBytes)
        }
        val sessionEncryptionOpsClient = mock<SessionEncryptionOpsClient>()
        val state = mock<SessionState> {
            on { toAvro(schemaRegistry, sessionEncryptionOpsClient) } doReturn avroState
        }

        val convertor = StateConvertor(
            schemaRegistry,
            sessionEncryptionOpsClient,
        )

        val bytes = convertor.toStateByteArray(
            state,
        )

        assertThat(bytes).isEqualTo(expectedBytes)
    }
}
