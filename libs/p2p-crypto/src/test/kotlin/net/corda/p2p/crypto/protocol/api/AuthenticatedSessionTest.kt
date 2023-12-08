package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.Session
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

class AuthenticatedSessionTest {

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)

    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"
    private val aliceX500Name = MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")

    // party A
    private val partyAMaxMessageSize = 1_000_000
    private val partyASessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(
        sessionId,
        setOf(ProtocolMode.AUTHENTICATION_ONLY),
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
    fun `session can be established between two parties and used for transmission of authenticated data successfully`() {
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
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256)
        )

        authenticationProtocolB.validateEncryptedExtensions(
            CertificateCheckMode.NoCertificate,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
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
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedSession

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val initiatorMsg = AuthenticatedDataMessage(
                authenticationResult.header, ByteBuffer.wrap(payload),
                ByteBuffer.wrap(authenticationResult.mac)
            )

            authenticatedSessionOnB.validateMac(initiatorMsg.header, initiatorMsg.payload.array(), initiatorMsg.authTag.array())
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val authenticationResult = authenticatedSessionOnB.createMac(payload)
            val responderMsg =
                AuthenticatedDataMessage(
                    authenticationResult.header,
                    ByteBuffer.wrap(payload), ByteBuffer.wrap(authenticationResult.mac)
                )

            authenticatedSessionOnA.validateMac(responderMsg.header, responderMsg.payload.array(), responderMsg.authTag.array())
        }
    }

    @Test
    fun `when MAC on data message is altered during transmission, validation fails with an error`() {
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
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
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
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedSession

        // Data exchange: A sends message to B, B receives corrupted data which fail validation.
        val payload = "ping".toByteArray(Charsets.UTF_8)
        val authenticationResult = authenticatedSessionOnA.createMac(payload)
        val initiatorMsg = AuthenticatedDataMessage(
            authenticationResult.header, ByteBuffer.wrap(payload),
            ByteBuffer.wrap(authenticationResult.mac)
        )

        assertThatThrownBy {
            authenticatedSessionOnB.validateMac(
                initiatorMsg.header,
                initiatorMsg.payload.array() + "0".toByteArray(Charsets.UTF_8), initiatorMsg.authTag.array()
            )
        }
            .isInstanceOf(InvalidMac::class.java)
    }

    @Test
    fun `when trying to create MAC for message larger than the agreed max message size, an exception is thrown`() {
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
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
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
        val authenticatedSessionOnA = authenticationProtocolA.getSession() as AuthenticatedSession
        val authenticatedSessionOnB = authenticationProtocolB.getSession() as AuthenticatedSession

        assertThatThrownBy { authenticatedSessionOnA.createMac(ByteArray(partyAMaxMessageSize + 1)) }
            .isInstanceOf(MessageTooLargeError::class.java)
            .hasMessageContaining(
                "Message's size (${partyAMaxMessageSize + 1} bytes) was larger than the max message " +
                    "size of the session ($partyAMaxMessageSize bytes)"
            )

        val payload = ByteArray(partyAMaxMessageSize + 1)
        val header = CommonHeader(MessageType.DATA, 1, "some-session-id", 4, Instant.now().toEpochMilli())
        assertThatThrownBy { authenticatedSessionOnB.validateMac(header, payload, ByteArray(0)) }
            .isInstanceOf(MessageTooLargeError::class.java)
            .hasMessageContaining(
                "Message's size (${partyAMaxMessageSize + 1} bytes) was larger than the max message" +
                    " size of the session ($partyAMaxMessageSize bytes)"
            )
    }

    @Test
    fun `toAvro return a correct avro object`() {
        val session = AuthenticatedSession(
            sessionId = "sessionId",
            outboundSecretKey = SecretKeySpec( "aaa".toByteArray(), "alg1"),
            inboundSecretKey = SecretKeySpec("bbb".toByteArray(), "alg2"),
            maxMessageSize = 100
        )

        val avro = session.toAvro()

        assertThat(avro.details).isInstanceOf(AuthenticatedSessionDetails::class.java)
    }

    @Test
    fun `toCorda return a correct session`() {
        val avro = Session(
            "sessionId",
            300,
            AuthenticatedSessionDetails(
                net.corda.data.p2p.crypto.protocol.SecretKeySpec(
                    "alg",
                    ByteBuffer.wrap(byteArrayOf(1)),
                ),
                net.corda.data.p2p.crypto.protocol.SecretKeySpec(
                    "alg-2",
                    ByteBuffer.wrap(byteArrayOf(3)),
                ),
            )
        )

        val session = avro.toCorda()

        assertThat(session).isInstanceOf(AuthenticatedSession::class.java)
    }
}
