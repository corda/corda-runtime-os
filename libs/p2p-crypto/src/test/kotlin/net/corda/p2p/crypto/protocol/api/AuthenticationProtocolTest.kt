package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.MIN_PACKET_SIZE
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.UUID

class AuthenticationProtocolTest {

    private val provider = BouncyCastleProvider.PROVIDER_NAME
    private val sessionId = UUID.randomUUID().toString()
    private val groupId = "some-group-id"

    // party A
    private val partyAMaxMessageSize = 1_000_000

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val aliceX500Name = MemberX500Name.parse("CN=alice, OU=MyUnit, O=MyOrg, L=London, S=London, C=GB")

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `no handshake message crosses the minimum value allowed for max message size`() {
        val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpecs.ECDSA_SHA256)
    }

    @Test
    fun `authentication protocol works successfully with ECDSA key algorithm`() {
        val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("ECDSA", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpecs.ECDSA_SHA256)
    }

    @Test
    fun `authentication protocol works successfully with RSA signatures`() {
        val signature = Signature.getInstance(SignatureSpecs.RSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpecs.RSA_SHA256)
    }

    @Test
    fun `authentication protocol verifies cert if CertificateCheckMode is CheckCertificate`() {
        val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()
        val ourCertificate = mutableListOf("")
        val certificateCheckMode = CertificateCheckMode.CheckCertificate(mock(), RevocationCheckMode.HARD_FAIL, mock())
        val certificateValidatorInitiator = mock<CertificateValidator>()
        val certificateValidatorResponder = mock<CertificateValidator>()

        executeProtocol(
            partyASessionKey, partyBSessionKey, signature, SignatureSpecs.ECDSA_SHA256,
            certificateCheckMode = certificateCheckMode, partyACertificate = ourCertificate, partyBCertificate = ourCertificate,
            certificateValidatorInitiator = certificateValidatorInitiator, certificateValidatorResponder = certificateValidatorResponder,
        )

        verify(certificateValidatorInitiator).validate(ourCertificate, aliceX500Name, partyBSessionKey.public)
        verify(certificateValidatorResponder).validate(ourCertificate, aliceX500Name, partyASessionKey.public)
    }

    @Test
    fun `authentication protocol methods are idempotent`() {
        val signature = Signature.getInstance(SignatureSpecs.ECDSA_SHA256.signatureName, provider)
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyASessionKey = keyPairGenerator.generateKeyPair()
        val partyBSessionKey = keyPairGenerator.generateKeyPair()

        executeProtocol(partyASessionKey, partyBSessionKey, signature, SignatureSpecs.ECDSA_SHA256, duplicateInvocations = true)
    }

    @Suppress("LongParameterList")
    private fun executeProtocol(
        partyASessionKey: KeyPair,
        partyBSessionKey: KeyPair,
        signature: Signature,
        signatureSpec: SignatureSpec,
        partyACertificate: List<PemCertificate>? = null,
        partyBCertificate: List<PemCertificate>? = null,
        duplicateInvocations: Boolean = false,
        certificateCheckMode: CertificateCheckMode = CertificateCheckMode.NoCertificate,
        certificateValidatorInitiator: CertificateValidator? = null,
        certificateValidatorResponder: CertificateValidator? = null,
    ) {
        val protocolInitiator = if (certificateValidatorInitiator != null) {
            AuthenticationProtocolInitiator(
                sessionId,
                setOf(ProtocolMode.AUTHENTICATION_ONLY),
                partyAMaxMessageSize,
                partyASessionKey.public,
                groupId,
                certificateCheckMode,
            ) { _, _, _ -> certificateValidatorInitiator }
        } else {
            AuthenticationProtocolInitiator(
                sessionId,
                setOf(ProtocolMode.AUTHENTICATION_ONLY),
                partyAMaxMessageSize,
                partyASessionKey.public,
                groupId,
                certificateCheckMode,
            )
        }
        val protocolResponder = if (certificateValidatorResponder != null) {
            AuthenticationProtocolResponder(
                sessionId,
                partyBMaxMessageSize,
            ) { _, _, _ -> certificateValidatorResponder }
        } else {
            AuthenticationProtocolResponder(
                sessionId,
                partyBMaxMessageSize,
            )
        }

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = protocolInitiator.generateInitiatorHello()
        assertThat(initiatorHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolResponder.receiveInitiatorHello(initiatorHelloMsg)
        if (duplicateInvocations) {
            assertThat(protocolInitiator.generateInitiatorHello()).isEqualTo(initiatorHelloMsg)
            protocolResponder.receiveInitiatorHello(initiatorHelloMsg)
        }

        // Step 2: responder sending hello message to initiator.
        val responderHelloMsg = protocolResponder.generateResponderHello()
        assertThat(responderHelloMsg.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolInitiator.receiveResponderHello(responderHelloMsg)
        if (duplicateInvocations) {
            assertThat(protocolResponder.generateResponderHello()).isEqualTo(responderHelloMsg)
            protocolInitiator.receiveResponderHello(responderHelloMsg)
        }

        // Both sides generate handshake secrets.
        protocolInitiator.generateHandshakeSecrets()
        protocolResponder.generateHandshakeSecrets()
        if (duplicateInvocations) {
            protocolInitiator.generateInitiatorHello()
            protocolResponder.generateHandshakeSecrets()
        }

        // Step 3: initiator sending handshake message and responder validating it.
        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyASessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = protocolInitiator.generateOurHandshakeMessage(
            partyBSessionKey.public,
            partyACertificate,
            signingCallbackForA,
        )
        assertThat(initiatorHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolResponder.validatePeerHandshakeMessage(
            initiatorHandshakeMessage,
            listOf(partyASessionKey.public to signatureSpec),
        )
        if (duplicateInvocations) {
            assertThat(protocolInitiator.generateOurHandshakeMessage(partyBSessionKey.public, partyACertificate, signingCallbackForA))
                .isEqualTo(initiatorHandshakeMessage)
            protocolResponder.validatePeerHandshakeMessage(
                initiatorHandshakeMessage,
                listOf(partyASessionKey.public to signatureSpec),
            )
        }

        protocolResponder.validateEncryptedExtensions(certificateCheckMode, setOf(ProtocolMode.AUTHENTICATION_ONLY), aliceX500Name)
        if (duplicateInvocations) {
            protocolResponder.validateEncryptedExtensions(certificateCheckMode, setOf(ProtocolMode.AUTHENTICATION_ONLY), aliceX500Name)
        }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = protocolResponder.generateOurHandshakeMessage(
            partyBSessionKey.public,
            partyBCertificate,
            signingCallbackForB,
        )
        assertThat(responderHandshakeMessage.toByteBuffer().array().size).isLessThanOrEqualTo(MIN_PACKET_SIZE)
        protocolInitiator.validatePeerHandshakeMessage(
            responderHandshakeMessage,
            aliceX500Name,
            listOf(partyBSessionKey.public to signatureSpec),
        )
        if (duplicateInvocations) {
            assertThat(protocolResponder.generateOurHandshakeMessage(partyBSessionKey.public, partyBCertificate, signingCallbackForB))
                .isEqualTo(responderHandshakeMessage)
            protocolInitiator.validatePeerHandshakeMessage(
                responderHandshakeMessage,
                aliceX500Name,
                listOf(partyBSessionKey.public to signatureSpec),
            )
        }
    }
}
