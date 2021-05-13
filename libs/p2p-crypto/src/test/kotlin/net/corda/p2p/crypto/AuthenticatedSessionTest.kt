package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.CommonHeader
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*

class AuthenticatedSessionTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    private val sessionId = UUID.randomUUID().toString() + "-4"

    // party A
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    @Test
    fun `session can be established between two parties and used for transmission of authenticated data successfully`() {
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
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(clientHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolB.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val clientMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnB.validateMac(clientMsg.header, clientMsg.payload, clientMsg.mac)
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnB.createMac(payload)
            val serverMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnA.validateMac(serverMsg.header, serverMsg.payload, serverMsg.mac)
        }
    }

    @Test
    fun `session can be established between two parties and used for transmission of authenticated data successfully with step 2 executed on separate component`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        authenticationProtocolA.receiveServerHello(serverHelloMsg)

        // Fronting component of responder sends data downstream so that protocol can be continued.
        val (privateKey, publicKey) = authenticationProtocolB.getDHKeyPair()
        val authenticationProtocolBDownstream = AuthenticationProtocolResponder.fromStep2(sessionId, listOf(Mode.AUTHENTICATION_ONLY), clientHelloMsg, serverHelloMsg, privateKey, publicKey)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolBDownstream.generateHandshakeSecrets()

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, signingCallbackForA)

        authenticationProtocolBDownstream.validatePeerHandshakeMessage(clientHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolBDownstream.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolBDownstream.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val clientMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnB.validateMac(clientMsg.header, clientMsg.payload, clientMsg.mac)
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnB.createMac(payload)
            val serverMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

            authenticatedSessionOnA.validateMac(serverMsg.header, serverMsg.payload, serverMsg.mac)
        }
    }

    @Test
    fun `when MAC on data message is altered during transmission, validation fails with an error`() {
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
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(clientHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage, partyBIdentityKey.public)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolB.getSession()

        // Data exchange: A sends message to B, B receives corrupted data which fail validation.
        val payload = "ping".toByteArray(Charsets.UTF_8)
        val authenticationResult = authenticatedSessionOnA.createMac(payload)
        val clientMsg = DataMessage(authenticationResult.header, payload, authenticationResult.mac)

        assertThatThrownBy { authenticatedSessionOnB.validateMac(clientMsg.header, clientMsg.payload + "0".toByteArray(Charsets.UTF_8), clientMsg.mac) }
            .isInstanceOf(InvalidMac::class.java)
    }

}

data class DataMessage(val header: CommonHeader, val payload: ByteArray, val mac: ByteArray)