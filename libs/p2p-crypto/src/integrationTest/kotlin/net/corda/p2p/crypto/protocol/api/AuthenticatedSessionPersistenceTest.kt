package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.Session.Companion.toCorda
import net.corda.v5.base.types.MemberX500Name
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.UUID

class AuthenticatedSessionPersistenceTest {

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
        setOf(ProtocolMode.AUTHENTICATION_ONLY),
        partyAMaxMessageSize,
        partyASessionKey.public,
        groupId,
        CertificateCheckMode.NoCertificate,
    )
    private val authenticationProtocolA: AuthenticationProtocolInitiator
        get() {
            val avro = _authenticationProtocolA.toAvro()
            _authenticationProtocolA = avro.toCorda {
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
            val avro = _authenticationProtocolB.toAvro()
            _authenticationProtocolB = avro.toCorda()
            return _authenticationProtocolB
        }
    private val authenticatedSessionOnA: AuthenticatedSession
        get() {
            return authenticationProtocolA.getSession().toAvro().toCorda() as AuthenticatedSession
        }
    private val authenticatedSessionOnB: AuthenticatedSession
        get() {
            return authenticationProtocolB.getSession().toAvro().toCorda() as AuthenticatedSession
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
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )

        authenticationProtocolB.validateEncryptedExtensions(
            CertificateCheckMode.NoCertificate,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
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
            val authenticationResult = authenticatedSessionOnA.createMac(payload)
            val initiatorMsg = AuthenticatedDataMessage(
                authenticationResult.header,
                ByteBuffer.wrap(payload),
                ByteBuffer.wrap(authenticationResult.mac),
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
                    ByteBuffer.wrap(payload),
                    ByteBuffer.wrap(authenticationResult.mac),
                )

            authenticatedSessionOnA.validateMac(responderMsg.header, responderMsg.payload.array(), responderMsg.authTag.array())
        }
    }
}
