package net.corda.p2p.crypto.protocol.api

import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolCommonDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolResponderDetails
import net.corda.data.p2p.crypto.protocol.ResponderStep
import net.corda.data.p2p.crypto.protocol.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.Companion.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.Security

class AuthenticationProtocolResponderTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `toAvro returns the correct object`() {
        val initiator = AuthenticationProtocolResponder(
            sessionId = "sessionId",
            ourMaxMessageSize = 500000,
        )

        val avro = initiator.toAvro()

        SoftAssertions.assertSoftly {
            assertThat(avro).isInstanceOf(AuthenticationProtocolResponderDetails::class.java)
            assertThat(avro.protocolCommonDetails.sessionId).isEqualTo("sessionId")
            assertThat(avro.protocolCommonDetails.ourMaxMessageSize).isEqualTo(500000)
            assertThat(avro.step).isEqualTo(ResponderStep.INIT)
            assertThat(avro.handshakeIdentityData).isNull()
            assertThat(avro.responderHandshakeMessage).isNull()
            assertThat(avro.encryptedExtensions).isNull()
            assertThat(avro.initiatorPublicKey).isNull()
        }
    }

    @Test
    fun `toCorda returns the correct object`() {
        val avro = AuthenticationProtocolResponderDetails(
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
        val initiator = avro.toCorda()

        SoftAssertions.assertSoftly {
            assertThat(initiator).isInstanceOf(AuthenticationProtocolResponder::class.java)
            assertThat(initiator.sessionId).isEqualTo("sessionId")
            assertThat(initiator.getSession()).isInstanceOf(AuthenticatedSession::class.java)
        }
    }
}
