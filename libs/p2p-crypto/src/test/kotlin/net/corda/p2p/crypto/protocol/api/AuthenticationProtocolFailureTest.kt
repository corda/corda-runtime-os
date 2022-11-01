package net.corda.p2p.crypto.protocol.api

import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

/**
 * Tests exercising behaviour of authentication protocol under malicious actions and/or invalid operations.
 */
class AuthenticationProtocolFailureTest {

    private val provider = BouncyCastleProvider()
    private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
    private val signature = Signature.getInstance(SignatureSpec.ECDSA_SHA256.signatureName, provider)
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
        CertificateCheckMode.NoCertificate
    )

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBSessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB =
        AuthenticationProtocolResponder(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize, CertificateCheckMode.NoCertificate
        )

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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        val modifiedInitiatorHandshakeMessage = InitiatorHandshakeMessage(
            initiatorHandshakeMessage.header,
            ByteBuffer.wrap(initiatorHandshakeMessage.encryptedData.array() + "0".toByte()), initiatorHandshakeMessage.authTag
        )
        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                modifiedInitiatorHandshakeMessage, aliceX500Name, partyASessionKey.public, SignatureSpec.ECDSA_SHA256
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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                initiatorHandshakeMessage, aliceX500Name, partyASessionKey.public, SignatureSpec.ECDSA_SHA256
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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)
        assertThatThrownBy {
            authenticationProtocolB.validatePeerHandshakeMessage(
                initiatorHandshakeMessage, aliceX500Name, wrongPublicKey, SignatureSpec.ECDSA_SHA256
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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            aliceX500Name,
            partyASessionKey.public,
            SignatureSpec.ECDSA_SHA256,
        )

        // Step 4: responder creating different signature than the one expected.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB)

        assertThatThrownBy {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage, aliceX500Name, partyBSessionKey.public, SignatureSpec.ECDSA_SHA256
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
            CertificateCheckMode.NoCertificate
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(
            sessionId, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION), partyBMaxMessageSize, CertificateCheckMode.NoCertificate
        )

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        assertThatThrownBy { authenticationProtocolB.generateResponderHello() }
            .isInstanceOf(NoCommonModeError::class.java)
    }

    @Test
    fun `session authentication fails if responder certificate validation fails`() {
        val certificateValidator = Mockito.mockConstruction(CertificateValidator::class.java) { mock, _ ->
            whenever(mock.validate(any(), any())).thenThrow(InvalidPeerCertificate("Invalid peer certificate"))
        }
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), ourCertificates, mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(
            sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize, certCheckMode
        )

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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        assertThrows<InvalidPeerCertificate> { authenticationProtocolB.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                aliceX500Name,
                partyASessionKey.public,
                SignatureSpec.ECDSA_SHA256,
            )
        }
        certificateValidator.close()
    }

    @Test
    fun `session authentication fails if initiator certificate validation fails`() {
        val certificateValidator = Mockito.mockConstruction(CertificateValidator::class.java)
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), ourCertificates, mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(
            sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize, certCheckMode
        )
        val certificateValidatorInitiator = certificateValidator.constructed()[0]!!
        //val certificateValidatorResponder = certificateValidator.constructed()[1]!!
        whenever(certificateValidatorInitiator.validate(any(), any())).thenThrow(InvalidPeerCertificate(""))

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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            aliceX500Name,
            partyASessionKey.public,
            SignatureSpec.ECDSA_SHA256,
        )

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB)

        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage, aliceX500Name, partyBSessionKey.public, SignatureSpec.ECDSA_SHA256
            )
        }

        certificateValidator.close()
    }

    @Test
    fun `session authentication fails for responder if initiator doesn't send a certificate`() {
        val certificateValidator = Mockito.mockConstruction(CertificateValidator::class.java)
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), ourCertificates, mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            CertificateCheckMode.NoCertificate
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(
            sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize, certCheckMode
        )

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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        assertThrows<InvalidPeerCertificate> { authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            aliceX500Name,
            partyASessionKey.public,
            SignatureSpec.ECDSA_SHA256,
        )
        }
        certificateValidator.close()
    }

    @Test
    fun `session authentication fails for initiator if responder doesn't send a certificate`() {
        val certificateValidator = Mockito.mockConstruction(CertificateValidator::class.java)
        val ourCertificates = listOf<String>()
        val certCheckMode = CertificateCheckMode.CheckCertificate(mock(), ourCertificates, mock())

        val authenticationProtocolA = AuthenticationProtocolInitiator(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            partyAMaxMessageSize,
            partyASessionKey.public,
            sessionId,
            certCheckMode
        )
        val authenticationProtocolB = AuthenticationProtocolResponder(
            sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize, CertificateCheckMode.NoCertificate
        )

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
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            aliceX500Name,
            partyASessionKey.public,
            SignatureSpec.ECDSA_SHA256,
        )

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB)

        assertThrows<InvalidPeerCertificate> {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage, aliceX500Name, partyBSessionKey.public, SignatureSpec.ECDSA_SHA256
            )
        }

        certificateValidator.close()
    }
}
