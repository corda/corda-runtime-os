package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.MIN_PACKET_SIZE
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

class AuthenticationProtocolTest {

    private val provider = BouncyCastleProvider()
    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"

    // party A
    private val partyAMaxMessageSize = 1_000_000

    // party B
    private val partyBMaxMessageSize = 1_500_000

    @Test
    fun `no handshake message crosses the minimum value allowed for max message size`() {
        val signature = Signature.getInstance(SignatureSpec.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpec.ECDSA_SHA256)
    }

    @Test
    fun `authentication protocol works successfully with RSA signatures`() {
        val signature = Signature.getInstance(SignatureSpec.RSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpec.RSA_SHA256)
    }

    @Test
    fun `authentication protocol methods are idempotent`() {
        val signature = Signature.getInstance(SignatureSpec.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpec.ECDSA_SHA256, true)
    }

    private fun executeProtocol(partyASessionKey: KeyPair,
                                partyBSessionKey: KeyPair,
                                signature: Signature,
                                signatureSpec: SignatureSpec,
                                duplicateInvocations: Boolean = false) {
        val protocolInitiator = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            groupId
        )
        val protocolResponder = AuthenticationProtocolResponder(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize)

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = protocolInitiator.generateInitiatorHello()
        assertThat(initiatorHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolResponder.receiveInitiatorHello(initiatorHelloMsg)
        if (duplicateInvocations) {
            assertThat(protocolInitiator.generateInitiatorHello()).isEqualTo(initiatorHelloMsg)
            protocolResponder.receiveInitiatorHello(initiatorHelloMsg)
        }

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = protocolResponder.generateResponderHello()
        assertThat(responderHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolInitiator.receiveResponderHello(responderHelloMsg)
        if (duplicateInvocations) {
            assertThat(protocolResponder.generateResponderHello()).isEqualTo(responderHelloMsg)
            protocolInitiator.receiveResponderHello(responderHelloMsg)
        }

        // Both sides generate handshake secrets.
        protocolInitiator.generateHandshakeSecrets()
        protocolResponder.generateHandshakeSecrets()
        if (duplicateInvocations) {
            protocolInitiator.generateInitiatorHello()
            protocolResponder.generateHandshakeSecrets()
        }

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)
        assertThat(initiatorHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage) { _, _, _ -> partyASessionKey.public to signatureSpec }
        if (duplicateInvocations) {
            assertThat(protocolInitiator.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA))
                .isEqualTo(initiatorHandshakeMessage)
            protocolResponder.validatePeerHandshakeMessage(initiatorHandshakeMessage) { _, _, _ ->
                partyASessionKey.public to signatureSpec
            }
        }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = protocolResponder.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB)
        assertThat(responderHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolInitiator.validatePeerHandshakeMessage(responderHandshakeMessage, partyBSessionKey.public, signatureSpec)
        if (duplicateInvocations) {
            assertThat(protocolResponder.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB))
                .isEqualTo(responderHandshakeMessage)
            protocolInitiator.validatePeerHandshakeMessage(responderHandshakeMessage, partyBSessionKey.public, signatureSpec)
        }
    }
}
