package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolCommonDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolInitiatorDetails
import net.corda.data.p2p.crypto.protocol.InitiatorStep
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.crypto.protocol.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator.Companion.toCorda
import net.corda.utilities.crypto.publicKeyFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.Security

class AuthenticationProtocolInitiatorTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val publicKeyPem = """
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxzfciOVARzcx7Xs8KnRj
KXlKJprH2DjNJI9NdGdVMzCvwyQSV1Tu72e3uLjkv22D/tIbwxBH5MkQkBx6GduN
3gF6wa4T8liQG/Gw60d8Lbuh3WoCqk7sFp+X8cgwMcaasi2nSnuC+yCdews40vjK
XfbQ7QyVUDZfbB+h09gXKkl6YfpsbU1yW1wN6uVOn7ReQu+0r+MXu8N/ZOeoVnTH
cZyj8BXGy1y5rL75SE01/3qwAvSAatoTtVtPlPuHn5U1nGDe3AoaS1LvrqIypJjo
ODGVZOuwAyZ6676J40yue06DiPpysNULHoZu3PEd+DEWKsCyPq3yYtB7E+HvLUAQ
AQIDAQAB
-----END PUBLIC KEY-----
    """

    private val publicKey by lazy {
        publicKeyPem.toPublicKey()
    }

    @Test
    fun `toAvro returns the correct object`() {
        val initiator = AuthenticationProtocolInitiator(
            sessionId = "sessionId",
            supportedModes = setOf(ProtocolMode.AUTHENTICATION_ONLY),
            ourMaxMessageSize = 500000,
            ourPublicKey = publicKey,
            groupId = "group",
            certificateCheckMode = CertificateCheckMode.CheckCertificate(
                listOf("one"),
                RevocationCheckMode.HARD_FAIL,
                mock(),
            ),
        )

        val avro = initiator.toAvro()

        assertSoftly {
            assertThat(avro).isInstanceOf(AuthenticationProtocolInitiatorDetails::class.java)
            assertThat(avro.protocolCommonDetails.sessionId).isEqualTo("sessionId")
            assertThat(avro.supportedModes).containsOnly(ProtocolMode.AUTHENTICATION_ONLY)
            assertThat(avro.groupId).isEqualTo("group")
            assertThat(avro.ourPublicKey.toPublicKey()).isEqualTo(publicKey)
            assertThat(avro.certificateCheckMode?.revocationCheckMode).isEqualTo(RevocationCheckMode.HARD_FAIL)
            assertThat(avro.certificateCheckMode?.truststore).containsOnly("one")
        }
    }

    @Test
    fun `toCorda returns the correct object`() {
        val avro = AuthenticationProtocolInitiatorDetails(
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
            publicKeyPem,
            "groupId",
            null,
            null,
        )
        val initiator = avro.toCorda { _ -> mock() }

        assertSoftly {
            assertThat(initiator).isInstanceOf(AuthenticationProtocolInitiator::class.java)
            assertThat(initiator.sessionId).isEqualTo("sessionId")
            assertThat(initiator.getSession()).isInstanceOf(AuthenticatedEncryptionSession::class.java)
        }
    }

    private fun String.toPublicKey(): PublicKey =
        publicKeyFactory(this.reader())!!
}
