package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.data.p2p.crypto.protocol.SecretKeySpec as AvroSecretKeySpec
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class AuthenticatedEncryptionSessionTest {

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)

    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"
    private val aliceX500Name =  MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")

    // party A
    private val partyAMaxMessageSize = 1_000_000
    private val partyASessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(
        sessionId,
        setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
        partyAMaxMessageSize,
        partyASessionKey.public,
        groupId,
        CertificateCheckMode.NoCertificate
    )

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBSessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

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
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA
        )

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(
                partyASessionKey.public to SignatureSpecs.ECDSA_SHA256,
            ),
        )

        authenticationProtocolB.validateEncryptedExtensions(
            CertificateCheckMode.NoCertificate,
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
            aliceX500Name
        )

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForB
        )

        authenticationProtocolA.validatePeerHandshakeMessage(
            responderHandshakeMessage,
            aliceX500Name,
            listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256,),
        )

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedEncryptionSession

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnA.encryptData(payload)
            val initiatorMsg = AuthenticatedEncryptedDataMessage(
                encryptionResult.header,
                ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag)
            )

            val decryptedPayload = authenticatedSessionOnB.decryptData(
                initiatorMsg.header, initiatorMsg.encryptedPayload.array(),
                initiatorMsg.authTag.array()
            )
            assertTrue(decryptedPayload.contentEquals(payload))
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnB.encryptData(payload)
            val responderMsg = AuthenticatedEncryptedDataMessage(
                encryptionResult.header,
                ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag)
            )

            val decryptedPayload = authenticatedSessionOnA.decryptData(
                responderMsg.header, responderMsg.encryptedPayload.array(),
                responderMsg.authTag.array()
            )
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
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA
        )

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )

        authenticationProtocolB.validateEncryptedExtensions(
            CertificateCheckMode.NoCertificate,
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
            aliceX500Name
        )

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForB
        )

        authenticationProtocolA.validatePeerHandshakeMessage(
            responderHandshakeMessage,
            aliceX500Name,
            listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256)
        )

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedEncryptionSession

        // Data exchange: A sends message to B, B receives corrupted data which fail validation.
        val payload = "ping".toByteArray(Charsets.UTF_8)
        val encryptionResult = authenticatedSessionOnA.encryptData(payload)
        val initiatorMsg =
            AuthenticatedDataMessage(
                encryptionResult.header,
                ByteBuffer.wrap(encryptionResult.encryptedPayload), ByteBuffer.wrap(encryptionResult.authTag)
            )

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

    @Test
    fun `when trying to encrypt message larger than the agreed max message size, an exception is thrown`() {
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
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA
        )

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )

        authenticationProtocolB.validateEncryptedExtensions(
            CertificateCheckMode.NoCertificate,
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
            aliceX500Name
        )

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForB
        )

        authenticationProtocolA.validatePeerHandshakeMessage(
            responderHandshakeMessage,
            aliceX500Name,
            listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )

        // Both sides generate session secrets
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedEncryptionSession

        Assertions.assertThatThrownBy { authenticatedSessionOnA.encryptData(ByteArray(partyAMaxMessageSize + 1)) }
            .isInstanceOf(MessageTooLargeError::class.java)
            .hasMessageContaining(
                "Message's size (${partyAMaxMessageSize + 1} bytes) was larger than the max message " +
                    "size of the session ($partyAMaxMessageSize bytes)"
            )
    }

    @Test
    fun `toAvro return a correct avro object`() {
        val session = AuthenticatedEncryptionSession(
            sessionId = "sessionId",
            outboundSecretKey = SecretKeySpec( "aaa".toByteArray(), "alg1"),
            outboundNonce = byteArrayOf(1),
            inboundSecretKey = SecretKeySpec("bbb".toByteArray(), "alg2"),
            inboundNonce = byteArrayOf(2, 3),
            maxMessageSize = 100
        )

        val avro = session.toAvro()

        assertThat(avro.details).isInstanceOf(AuthenticatedEncryptionSessionDetails::class.java)
    }

    @Test
    fun `toCorda return a correct session`() {
        val avro = Session(
            "sessionId",
            300,
            AuthenticatedEncryptionSessionDetails(
                AvroSecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                ByteBuffer.wrap(byteArrayOf(2)),
                AvroSecretKeySpec(
                    "alg-2",
                    ByteBuffer.wrap(byteArrayOf(3)),
                ),
                ByteBuffer.wrap(byteArrayOf(3)),
            )
        )

        val session = avro.toCorda()

        assertThat(session).isInstanceOf(AuthenticatedEncryptionSession::class.java)
    }
}
