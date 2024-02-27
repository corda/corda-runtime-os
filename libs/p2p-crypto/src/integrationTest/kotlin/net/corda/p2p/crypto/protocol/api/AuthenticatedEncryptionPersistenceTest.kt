package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.UUID

class AuthenticatedEncryptionPersistenceTest {
    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)

    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"
    private val aliceX500Name = MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")

    // party A
    private val partyAMaxMessageSize = 1_000_000
    private val partyASessionKey = keyPairGenerator.generateKeyPair()
    private var _authenticationProtocolA = AuthenticationProtocolInitiator(
        sessionId,
        setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
        partyAMaxMessageSize,
        partyASessionKey.public,
        groupId,
        CertificateCheckMode.NoCertificate,
    )
    private val authenticationProtocolA: AuthenticationProtocolInitiator
        get() {
            val details = _authenticationProtocolA.toAvro()
            _authenticationProtocolA = details.toCorda {
                RevocationCheckResponse()
            }
            return _authenticationProtocolA
        }

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBSessionKey = keyPairGenerator.generateKeyPair()
    private var _authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)
    private val authenticationProtocolB: AuthenticationProtocolResponder
        get() {
            val details = _authenticationProtocolB.toAvro()
            _authenticationProtocolB = details.toCorda()
            return _authenticationProtocolB
        }

    private val authenticatedSessionOnA: AuthenticatedEncryptionSession
        get() {
            return authenticationProtocolA.getSession().toAvro().toCorda() as AuthenticatedEncryptionSession
        }
    private val authenticatedSessionOnB: AuthenticatedEncryptionSession
        get() {
            return authenticationProtocolB.getSession().toAvro().toCorda() as AuthenticatedEncryptionSession
        }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `session can be established while being persisted and restored`() {
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
            signingCallbackForA,
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
            aliceX500Name,
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
            signingCallbackForB,
        )

        authenticationProtocolA.validatePeerHandshakeMessage(
            responderHandshakeMessage,
            aliceX500Name,
            listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )

        for (i in 1..3) {
            // Data exchange: A sends message to B, which decrypts and validates it
            val payload = "ping $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnA.encryptData(payload)
            val initiatorMsg = AuthenticatedEncryptedDataMessage(
                encryptionResult.header,
                ByteBuffer.wrap(encryptionResult.encryptedPayload),
                ByteBuffer.wrap(encryptionResult.authTag),
            )

            val decryptedPayload = authenticatedSessionOnB.decryptData(
                initiatorMsg.header,
                initiatorMsg.encryptedPayload.array(),
                initiatorMsg.authTag.array(),
            )
            assertThat(decryptedPayload).isEqualTo(payload)
        }

        for (i in 1..3) {
            // Data exchange: B -> A
            val payload = "pong $i".toByteArray(Charsets.UTF_8)
            val encryptionResult = authenticatedSessionOnB.encryptData(payload)
            val responderMsg = AuthenticatedEncryptedDataMessage(
                encryptionResult.header,
                ByteBuffer.wrap(encryptionResult.encryptedPayload),
                ByteBuffer.wrap(encryptionResult.authTag),
            )

            val decryptedPayload = authenticatedSessionOnA.decryptData(
                responderMsg.header,
                responderMsg.encryptedPayload.array(),
                responderMsg.authTag.array(),
            )
            assertThat(decryptedPayload).isEqualTo(payload)
        }
    }
}
