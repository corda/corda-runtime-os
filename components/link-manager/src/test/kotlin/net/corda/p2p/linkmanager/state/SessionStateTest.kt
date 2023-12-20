package net.corda.p2p.linkmanager.state

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolCommonDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolInitiatorDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolResponderDetails
import net.corda.data.p2p.crypto.protocol.InitiatorStep
import net.corda.data.p2p.crypto.protocol.ResponderStep
import net.corda.data.p2p.crypto.protocol.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.p2p.linkmanager.state.SessionState.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.avro.specific.SpecificRecordBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.Security
import net.corda.data.p2p.state.SessionState as AvroSessionData

class SessionStateTest {
    companion object {
        private val publicKeyPem = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmw0wYaKXc3M/aU8YfgdP
lnxOEsUbYuroh0S5OypTY9Fid75bdpV8auJCPZYOErBAtH9iD3t347ZhEyH6IXiS
Mw/tcN4ZGE4MGK7whR7HJ9560VbzS8RCUTjWYEHyRD4zvR7zk73tvKUUnP20a9ox
X3ObYFwFpIaQ+sq06qffEASk0S3sTWXQwXPfxNgrGcsyeDzztjjbQI1lXl5/N1Z+
sP1IEWgiH9eVcdsYcS2qn858tq+YFRZeMV2JRPHxiLylZA5u0T3GXQ4Bm95mkJmz
oPrD4+MHOuE9mzdCly9ZCUTU21tziQ2XlLQtlB4+IQJV5XM5VGyP3n+JrFgsF79x
YQIDAQAB
-----END PUBLIC KEY-----
        """.trimIndent().replace("\n", System.lineSeparator())

        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }
    private val decrypted = byteArrayOf(1)
    private val encrypted = byteArrayOf(2)
    private val serialized = ByteBuffer.wrap(decrypted)
    private val encryptionClient = mock<SessionEncryptionOpsClient> {
        on { decryptSessionData(eq(encrypted), anyOrNull()) } doReturn decrypted
        on { encryptSessionData(eq(decrypted), anyOrNull()) } doReturn encrypted
    }
    private val avroSchemaRegistry = mock<AvroSchemaRegistry>()
    private val message = mock<LinkOutMessage>()

    private fun testToCorda(
        avroObject: SpecificRecordBase,
    ) {
        whenever(avroSchemaRegistry.getClassType(serialized)).doReturn(avroObject::class.java)
        whenever(avroSchemaRegistry.deserialize(serialized, avroObject::class.java, null)).doReturn(avroObject)
        val sessionData = AvroSessionData(
            message,
            ByteBuffer.wrap(encrypted),
        )

        val data = sessionData.toCorda(
            avroSchemaRegistry,
            encryptionClient,
            mock(),
        )

        assertSoftly {
            assertThat(data.sessionData.toAvro()).isEqualTo(avroObject)
            assertThat(data.message).isEqualTo(message)
        }
    }

    @Test
    fun `test toCorda for AuthenticationProtocolInitiator`() {
        val testObject = AuthenticationProtocolInitiatorDetails(
            AuthenticationProtocolCommonDetails(
                "sessionId",
                500000,
                Session(
                    "sessionId",
                    300,
                    AuthenticatedEncryptionSessionDetails(
                        SecretKeySpec(
                            "alg",
                            ByteBuffer.wrap(byteArrayOf(1)),
                        ),
                        ByteBuffer.wrap(byteArrayOf(2)),
                        SecretKeySpec(
                            "alg-2",
                            ByteBuffer.wrap(byteArrayOf(3)),
                        ),
                        ByteBuffer.wrap(byteArrayOf(3)),
                    ),
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            ),
            InitiatorStep.SESSION_ESTABLISHED,
            listOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION),
            "$publicKeyPem${System.lineSeparator()}",
            "groupId",
            null,
            null,
        )
        testToCorda(testObject)
    }

    @Test
    fun `test toCorda for AuthenticationProtocolResponder`() {
        val testObject = AuthenticationProtocolResponderDetails(
            AuthenticationProtocolCommonDetails(
                "sessionId",
                500000,
                Session(
                    "sessionId",
                    300,
                    AuthenticatedSessionDetails(
                        SecretKeySpec(
                            "alg",
                            ByteBuffer.wrap(byteArrayOf(1)),
                        ),
                        SecretKeySpec(
                            "alg-2",
                            ByteBuffer.wrap(byteArrayOf(3)),
                        ),
                    ),
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            ),
            ResponderStep.SESSION_ESTABLISHED,
            null,
            null,
            null,
            null,
        )

        testToCorda(testObject)
    }

    @Test
    fun `test toCorda for AuthenticatedSession`() {
        val testObject = Session(
            "sessionId",
            300,
            AuthenticatedSessionDetails(
                SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                SecretKeySpec(
                    "alg-2",
                    ByteBuffer.wrap(byteArrayOf(3)),
                ),
            ),
        )

        testToCorda(testObject)
    }

    @Test
    fun `test toCorda for AuthenticatedEncryptionSession`() {
        val testObject = Session(
            "sessionId",
            300,
            AuthenticatedEncryptionSessionDetails(
                SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                ByteBuffer.wrap(byteArrayOf(2)),
                SecretKeySpec(
                    "alg-2",
                    ByteBuffer.wrap(byteArrayOf(3)),
                ),
                ByteBuffer.wrap(byteArrayOf(3)),
            ),
        )

        testToCorda(testObject)
    }

    @Test
    fun `test toCorda with unexpected type`() {
        assertThrows<CordaRuntimeException> {
            testToCorda(mock())
        }
    }

    @Test
    fun `test toAvro`() {
        val avroSession = Session(
            "sessionId",
            300,
            AuthenticatedSessionDetails(
                SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                SecretKeySpec(
                    "alg-2",
                    ByteBuffer.wrap(byteArrayOf(3)),
                ),
            ),
        )
        val data = avroSession.toCorda() as AuthenticatedSession
        whenever(avroSchemaRegistry.serialize(avroSession)).thenReturn(serialized)
        val sessionState = SessionState(
            message = message,
            sessionData = data,
        )

        val avroSessionData = sessionState.toAvro(
            avroSchemaRegistry = avroSchemaRegistry,
            encryptionClient = encryptionClient,
        )

        assertSoftly {
            assertThat(avroSessionData.message).isSameAs(message)
            assertThat(avroSessionData.encryptedSessionData.array()).isEqualTo(encrypted)
        }
    }
}
