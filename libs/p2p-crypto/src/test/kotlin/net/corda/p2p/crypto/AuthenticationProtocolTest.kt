package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.MIN_PACKET_SIZE
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

class AuthenticationProtocolTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    private val sessionId = UUID.randomUUID().toString()

    // party A
    private val partyAMaxMessageSize = 1_000_000
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, listOf(ProtocolMode.AUTHENTICATION_ONLY), partyAMaxMessageSize)

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, listOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize)

    private val groupId = "some-group-id"

    @Test
    fun `no handshake message crosses the minimum value allowed for max message size`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        assertThat(initiatorHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        assertThat(responderHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)
        assertThat(initiatorHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)
        assertThat(responderHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)
    }

}