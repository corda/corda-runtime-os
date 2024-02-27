package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.UUID

/**
 * Tests exercising behaviour of authentication protocol under malicious actions and/or invalid operations.
 */
class AuthenticationProtocolFailureTest {

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)
    private val aliceX500Name = MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")

    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"

    // party A
    private val partyAMaxMessageSize = 1_000_000
    private val partyASessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolA = AuthenticationProtocolInitiator(
        sessionId,
        setOf(ProtocolMode.AUTHENTICATION_ONLY),
        partyAMaxMessageSize,
        partyASessionKey.public,
        groupId,
        CertificateCheckMode.NoCertificate,
    )

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBSessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)
    private val certificateValidator = mock<CertificateValidator>()

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

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
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA,
        )

        val modifiedInitiatorHandshakeMessage = InitiatorHandshakeMessage(
            initiatorHandshakeMessage.header,
            ByteBuffer.wrap(initiatorHandshakeMessage.encryptedData.array() + "0".toByte()),
            initiatorHandshakeMessage.authTag,
        )
        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                modifiedInitiatorHandshakeMessage,
                listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
            )
        }
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
            signature.initSign(partyASessionKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA,
        )

        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
            )
        }
            .isInstanceOf(InvalidHandshakeMessageException::class.java)
    }

    @Test
    fun `session authentication fails if key provided at step 3 does not match the one given by initiator`() {
        val wrongPublicKey = keyPairGenerator.generateKeyPair().public

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        // Both sides generate handshake secrets.
        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        // Step 3: the provided public key does not match the one given by the initiator at step 1.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyASessionKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForA,
        )
        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                listOf(wrongPublicKey to SignatureSpecs.ECDSA_SHA256),
            )
        }
            .isInstanceOf(WrongPublicKeyHashException::class.java)
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

        // Step 4: responder creating different signature than the one expected.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(
            partyBSessionKey.public,
            null,
            signingCallbackForB,
        )

        assertThatThrownBy {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                aliceX500Name,
                listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256),
            )
        }
            .isInstanceOf(InvalidHandshakeMessageException::class.java)
    }

    @Test
    fun `session authentication fails if two parties do not share a common supported protocol mode`() {
        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            CertificateCheckMode.NoCertificate,
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)

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

        assertThrows<NoCommonModeError> {
            authenticationProtocolB.validateEncryptedExtensions(
                CertificateCheckMode.NoCertificate,
                setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
                aliceX500Name,
            )
        }
    }

    @Test
    fun `session authentication fails if responder certificate validation fails`() {
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), mock(), mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode,
        ) { _, _, _ -> certificateValidator }
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)
        whenever(certificateValidator.validate(any(), any(), any()))
            .thenThrow(InvalidPeerCertificate("Invalid peer certificate"))

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
            ourCertificates,
            signingCallbackForA,
        )
        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )
        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolB.validateEncryptedExtensions(certCheckMode, setOf(ProtocolMode.AUTHENTICATION_ONLY), aliceX500Name)
        }
    }

    @Test
    fun `session authentication fails if initiator certificate validation fails`() {
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), mock(), mock())
        val certificateValidatorResponder = mock<CertificateValidator>()

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode,
        ) { _, _, _ -> certificateValidator }
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize) { _, _, _ ->
            certificateValidatorResponder
        }
        whenever(certificateValidator.validate(any(), any(), any())).thenThrow(InvalidPeerCertificate(""))

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
            ourCertificates,
            signingCallbackForA,
        )

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(partyASessionKey.public to SignatureSpecs.ECDSA_SHA256),
        )
        authenticationProtocolB.validateEncryptedExtensions(certCheckMode, setOf(ProtocolMode.AUTHENTICATION_ONLY), aliceX500Name)

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(
            partyBSessionKey.public,
            ourCertificates,
            signingCallbackForB,
        )

        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                aliceX500Name,
                listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256),
            )
        }
    }

    @Test
    fun `session authentication fails for responder if initiator doesn't send a certificate`() {
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), mock(), mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            CertificateCheckMode.NoCertificate,
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize) { _, _, _ -> certificateValidator }

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

        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolB.validateEncryptedExtensions(certCheckMode, setOf(ProtocolMode.AUTHENTICATION_ONLY), aliceX500Name)
        }
    }

    @Test
    fun `session authentication fails for initiator if responder doesn't send a certificate`() {
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), mock(), mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode,
        ) { _, _, _ -> certificateValidator }
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, partyBMaxMessageSize)

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
            ourCertificates,
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

        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                aliceX500Name,
                listOf(partyBSessionKey.public to SignatureSpecs.ECDSA_SHA256),
            )
        }
    }
}
