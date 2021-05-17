package net.corda.p2p.crypto

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
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
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    private val groupId = "some-group-id"

    @Test
    fun `session authentication fails if malicious actor changes initiator's handshake message`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        authenticationProtocolA.receiveServerHello(serverHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        val modifiedClientHandshakeMessage = clientHandshakeMessage.copy(encryptedData = clientHandshakeMessage.encryptedData + "0".toByteArray(Charsets.UTF_8))
        assertThatThrownBy { authenticationProtocolB.validatePeerHandshakeMessage(modifiedClientHandshakeMessage) { partyAIdentityKey.public } }
                .isInstanceOf(InvalidHandshakeMessage::class.java)
    }

    @Test
    fun `session authentication fails if initiator's ClientPartyVerify signature is invalid`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        authenticationProtocolA.receiveServerHello(serverHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator creating different signature than the one expected.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        assertThatThrownBy { authenticationProtocolB.validatePeerHandshakeMessage(clientHandshakeMessage) { partyAIdentityKey.public } }
                .isInstanceOf(InvalidHandshakeMessage::class.java)
    }

    @Test
    fun `session authentication fails if responder's ServerPartyVerify signature is invalid`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        authenticationProtocolA.receiveServerHello(serverHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(clientHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder creating different signature than the one expected.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        assertThatThrownBy { authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage, partyBIdentityKey.public) }
                .isInstanceOf(InvalidHandshakeMessage::class.java)
    }


}