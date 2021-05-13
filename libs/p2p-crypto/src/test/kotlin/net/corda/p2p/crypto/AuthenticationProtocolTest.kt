package net.corda.p2p.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.*

class AuthenticationProtocolTest {

    private val logger = LoggerFactory.getLogger(AuthenticationProtocolTest::class.java.name)
    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance("ECDSA", provider)

    val sessionId = UUID.randomUUID().toString() + "-4"

    // party A
    private val partyAIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    // party B
    private val partyBIdentityKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, listOf(Mode.AUTHENTICATION_ONLY))

    @Test
    fun `test key exchange protocol between two components`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        logger.info("Party A sending client hello: $clientHelloMsg")
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        logger.info("Party B sending server hello: $serverHelloMsg")
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
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, signingCallbackForA)
        logger.info("Party A sending $clientHandshakeMessage")

        authenticationProtocolB.validatePeerHandshakeMessage(clientHandshakeMessage)

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)
        logger.info("Party B sending $serverHandshakeMessage")

        authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolB.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val header = sessionId.toByteArray(Charsets.UTF_8)
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val (encryptedPayloadFromAToB, tagFromAToB, messageNonce) = authenticatedSessionOnA.encrypt(header, payload)
            val clientMsg = DataMessage(header, encryptedPayloadFromAToB, tagFromAToB)

            val plaintextReceivedByB = authenticatedSessionOnB.decrypt(clientMsg.header, clientMsg.encryptedPayload, clientMsg.tag, messageNonce)
            val plaintextReceivedByBDeserialised = String(plaintextReceivedByB, Charsets.UTF_8)
            logger.info("B received $plaintextReceivedByBDeserialised from A")
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val header = sessionId.toByteArray(Charsets.UTF_8)
            val serverPayload = "pong $i".toByteArray(Charsets.UTF_8)
            val (encryptedPayloadFromBToA, tagFromBToA, messageNonce) = authenticatedSessionOnB.encrypt(header, serverPayload)
            val serverMsg = DataMessage(header, encryptedPayloadFromBToA, tagFromBToA)

            val plaintextReceivedByA = authenticatedSessionOnA.decrypt(serverMsg.header, serverMsg.encryptedPayload, serverMsg.tag, messageNonce)
            val plaintextReceivedByADeserialised = String(plaintextReceivedByA, Charsets.UTF_8)
            logger.info("A received $plaintextReceivedByADeserialised from B")
        }
    }

    @Test
    fun `test key exchange protocol between two components, where step 2 is completed on fronting component and keys are pushed downstream`() {
        // Step 1: initiator sending hello message to responder.
        val clientHelloMsg = authenticationProtocolA.generateClientHello()
        println("Party A sending client hello: $clientHelloMsg")
        authenticationProtocolB.receiveClientHello(clientHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val serverHelloMsg = authenticationProtocolB.generateServerHello()
        println("Party B sending server hello: $serverHelloMsg")
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
        val clientHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, signingCallbackForA)
        println("Party A sending $clientHandshakeMessage")

        authenticationProtocolBDownstream.validatePeerHandshakeMessage(clientHandshakeMessage)

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val serverHandshakeMessage = authenticationProtocolBDownstream.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)
        println("Party B sending $serverHandshakeMessage")

        authenticationProtocolA.validatePeerHandshakeMessage(serverHandshakeMessage)

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession()
        val authenticatedSessionOnB = authenticationProtocolBDownstream.getSession()

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val header = sessionId.toByteArray(Charsets.UTF_8)
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val (encryptedPayloadFromAToB, tagFromAToB, messageNonce) = authenticatedSessionOnA.encrypt(header, payload)
            val clientMsg = DataMessage(header, encryptedPayloadFromAToB, tagFromAToB)

            val plaintextReceivedByB = authenticatedSessionOnB.decrypt(clientMsg.header, clientMsg.encryptedPayload, clientMsg.tag, messageNonce)
            val plaintextReceivedByBDeserialised = String(plaintextReceivedByB, Charsets.UTF_8)
            println("B received $plaintextReceivedByBDeserialised from A")
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val header = sessionId.toByteArray(Charsets.UTF_8)
            val serverPayload = "pong".toByteArray(Charsets.UTF_8)
            val (encryptedPayloadFromBToA, tagFromBToA, messageNonce) = authenticatedSessionOnB.encrypt(header, serverPayload)
            val serverMsg = DataMessage(header, encryptedPayloadFromBToA, tagFromBToA)

            val plaintextReceivedByA = authenticatedSessionOnA.decrypt(serverMsg.header, serverMsg.encryptedPayload, serverMsg.tag, messageNonce)
            val plaintextReceivedByADeserialised = String(plaintextReceivedByA, Charsets.UTF_8)
            println("A received $plaintextReceivedByADeserialised from B")
        }
    }

}

data class DataMessage(val header: ByteArray, val encryptedPayload: ByteArray, val tag: ByteArray)