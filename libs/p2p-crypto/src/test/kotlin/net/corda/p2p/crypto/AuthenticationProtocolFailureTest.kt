package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*

/**
 * Tests exercising behaviour of authentication protocol under malicious actions and/or invalid operations.
 */
class AuthenticationProtocolFailureTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    private val sessionId = UUID.randomUUID().toString()

    // party A
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY))

    private val groupId = "some-group-id"

    @Test
    fun `session authentication fails if malicious actor changes initiator's handshake message`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
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

        val modifiedInitiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorHandshakeMessage.header, ByteBuffer.wrap(initiatorHandshakeMessage.encryptedData.array() + "0".toByte()), initiatorHandshakeMessage.authTag)
        assertThatThrownBy { authenticationProtocolB.validatePeerHandshakeMessage(modifiedInitiatorHandshakeMessage) { partyAIdentityKey.public } }
                .isInstanceOf(InvalidHandshakeMessageException::class.java)
    }

    @Test
    fun `session authentication fails if initiator's InitiatorPartyVerify signature is invalid`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator creating different signature than the one expected.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        assertThatThrownBy { authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public } }
                .isInstanceOf(InvalidHandshakeMessageException::class.java)
    }

    @Test
    fun `session authentication fails if responder's ResponderPartyVerify signature is invalid`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
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

        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder creating different signature than the one expected.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        assertThatThrownBy { authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public) }
                .isInstanceOf(InvalidHandshakeMessageException::class.java)
    }

    @Test
    fun `session authentication fails if two parties do not share a common supported protocol mode`() {
        val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY))
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION))

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        assertThatThrownBy { authenticationProtocolB.generateResponderHello() }
            .isInstanceOf(NoCommonModeError::class.java)
    }


}