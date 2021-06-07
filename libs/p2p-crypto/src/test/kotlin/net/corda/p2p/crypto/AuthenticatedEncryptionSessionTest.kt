package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.DecryptionFailedError
import org.assertj.core.api.Assertions
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

class AuthenticatedEncryptionSessionTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    private val sessionId = UUID.randomUUID().toString()

    // party A
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION))

    private val groupId = "some-group-id"

    @Test
    fun `session can be established between two parties and used for transmission of authenticated and encrypted data successfully`() {
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

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedEncryptionSession

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnA.encryptData(payload)
            val initiatorMsg = AuthenticatedEncryptedDataMessage(encryptionResult.header, ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag))

            val decryptedPayload = authenticatedSessionOnB.decryptData(initiatorMsg.header, initiatorMsg.encryptedPayload.array(), initiatorMsg.authTag.array())
            assertTrue(decryptedPayload.contentEquals(payload))
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnB.encryptData(payload)
            val responderMsg = AuthenticatedEncryptedDataMessage(encryptionResult.header, ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag))

            val decryptedPayload = authenticatedSessionOnA.decryptData(responderMsg.header, responderMsg.encryptedPayload.array(), responderMsg.authTag.array())
            assertTrue(decryptedPayload.contentEquals(payload))
        }
    }

    @Test
    fun `session can be established between two parties and used for transmission of authenticated and encrypted data successfully with step 2 executed on separate component`() {
        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Fronting component of responder sends data downstream so that protocol can be continued.
        val (privateKey, publicKey) = authenticationProtocolB.getDHKeyPair()
        val authenticationProtocolBDownstream = AuthenticationProtocolResponder.fromStep2(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), initiatorHelloMsg, responderHelloMsg, privateKey, publicKey)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolBDownstream.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolBDownstream.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolBDownstream.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession
        val authenticatedSessionOnB = authenticationProtocolBDownstream.getSession() as AuthenticatedEncryptionSession

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnA.encryptData(payload)
            val initiatorMsg = AuthenticatedEncryptedDataMessage(encryptionResult.header, ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag))

            val decryptedPayload = authenticatedSessionOnB.decryptData(initiatorMsg.header, initiatorMsg.encryptedPayload.array(), initiatorMsg.authTag.array())
            assertTrue(decryptedPayload.contentEquals(payload))
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnB.encryptData(payload)
            val responderMsg = AuthenticatedEncryptedDataMessage(encryptionResult.header, ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag) )

            val decryptedPayload = authenticatedSessionOnA.decryptData(responderMsg.header, responderMsg.encryptedPayload.array(), responderMsg.authTag.array())
            assertTrue(decryptedPayload.contentEquals(payload))
        }
    }

    @Test
    fun `when data message is altered during transmission, decryption fails with an error`() {
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

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedEncryptionSession

        // Data exchange: A sends message to B, B receives corrupted data which fail validation.
        val payload = "ping".toByteArray(Charsets.UTF_8)
        val encryptionResult = authenticatedSessionOnA.encryptData(payload)
        val initiatorMsg = AuthenticatedDataMessage(encryptionResult.header, ByteBuffer.wrap(encryptionResult.encryptedPayload) , ByteBuffer.wrap(encryptionResult.authTag))

        Assertions.assertThatThrownBy {
            val modifiedHeader = initiatorMsg.header
            modifiedHeader.sessionId = "some-other-session"
            authenticatedSessionOnB.decryptData(
                modifiedHeader,
                initiatorMsg.payload.array(),
                initiatorMsg.authTag.array() + "0".toByteArray(Charsets.UTF_8)
            )
        }.isInstanceOf(DecryptionFailedError::class.java)
            .hasMessageContaining("Decryption failed due to bad authentication tag.")

        Assertions.assertThatThrownBy {
            authenticatedSessionOnB.decryptData(
                initiatorMsg.header,
                initiatorMsg.payload.array() + "0".toByteArray(Charsets.UTF_8),
                initiatorMsg.authTag.array()
            )
        }.isInstanceOf(DecryptionFailedError::class.java)
         .hasMessageContaining("Decryption failed due to bad authentication tag.")

        Assertions.assertThatThrownBy {
            authenticatedSessionOnB.decryptData(
                initiatorMsg.header,
                initiatorMsg.payload.array(),
                initiatorMsg.authTag.array() + "0".toByteArray(Charsets.UTF_8)
            )
        }.isInstanceOf(DecryptionFailedError::class.java)
            .hasMessageContaining("Decryption failed due to bad authentication tag.")
    }

}