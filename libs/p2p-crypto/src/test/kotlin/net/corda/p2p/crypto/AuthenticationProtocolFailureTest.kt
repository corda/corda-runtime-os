package net.corda.p2p.crypto

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder.KeyLookupResult
import net.corda.p2p.crypto.protocol.api.InvalidHandshakeMessageException
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import net.corda.p2p.crypto.protocol.api.WrongPublicKeyHashException
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test
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
        groupId
    )

    // party B
    private val partyBMaxMessageSize = 1_500_000
    private val partyBSessionKey = keyPairGenerator.generateKeyPair()
    private val authenticationProtocolB =
        AuthenticationProtocolResponder<Unit>(
            sessionId,
            setOf(ProtocolMode.AUTHENTICATION_ONLY), partyBMaxMessageSize
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
            authenticationProtocolB.validatePeerHandshakeMessage(modifiedInitiatorHandshakeMessage) { _ ->
                KeyLookupResult(partyASessionKey.public, SignatureSpec.ECDSA_SHA256, Unit)
            }
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
            authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) {
                KeyLookupResult(partyASessionKey.public, SignatureSpec.ECDSA_SHA256, Unit)
            }
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
            authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) {
                KeyLookupResult(wrongPublicKey, SignatureSpec.ECDSA_SHA256, Unit)
            }
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

        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) {
            KeyLookupResult(partyASessionKey.public, SignatureSpec.ECDSA_SHA256, Unit)
        }

        // Step 4: responder creating different signature than the one expected.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBSessionKey.private)
            signature.update(data + "0".toByteArray(Charsets.UTF_8))
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBSessionKey.public, signingCallbackForB)

        assertThatThrownBy {
            authenticationProtocolA.validatePeerHandshakeMessage(
                responderHandshakeMessage, partyBSessionKey.public, SignatureSpec.ECDSA_SHA256
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
            sessionId
        )
        val authenticationProtocolB = AuthenticationProtocolResponder<Unit>(
            sessionId, setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION), partyBMaxMessageSize
        )

        // Step 1: initiator sending hello message to responder.
        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        // Step 2: responder sending hello message to initiator.
        assertThatThrownBy { authenticationProtocolB.generateResponderHello() }
            .isInstanceOf(NoCommonModeError::class.java)
    }
}
