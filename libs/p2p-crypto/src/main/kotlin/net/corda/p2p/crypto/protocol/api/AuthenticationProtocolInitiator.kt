package net.corda.p2p.crypto.protocol.api

import net.corda.p2p.crypto.protocol.AuthenticationProtocol
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SIG_PAD
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SIG_PAD
import net.corda.p2p.crypto.protocol.data.InitiatorHandshakeMessage
import net.corda.p2p.crypto.protocol.data.InitiatorHelloMessage
import net.corda.p2p.crypto.protocol.data.CommonHeader
import net.corda.p2p.crypto.protocol.data.MessageType
import net.corda.p2p.crypto.protocol.data.ResponderHandshakeMessage
import net.corda.p2p.crypto.protocol.data.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.toByteArray
import net.corda.p2p.crypto.util.calculateMac
import net.corda.p2p.crypto.util.decrypt
import net.corda.p2p.crypto.util.encryptWithAssociatedData
import net.corda.p2p.crypto.util.hash
import net.corda.p2p.crypto.util.perform
import net.corda.p2p.crypto.util.verify
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.AEADBadTagException

/**
 * The initiator side of the session authentication protocol.
 *
 * This class expects clients to call methods for each step in sequence and only once, i.e.:
 * - [generateInitiatorHello]
 * - [receiveResponderHello]
 * - [generateHandshakeSecrets]
 * - [generateOurHandshakeMessage]
 * - [validatePeerHandshakeMessage]
 * - [getSession]
 *
 * The [step] variable can be used to avoid calling methods when they have been called already (i.e. because of a duplicate message).
 *
 * This class is not thread-safe, which means clients that want to use it from different threads need to perform external synchronisation.
 */
class AuthenticationProtocolInitiator(private val sessionId: String, private val supportedModes: List<Mode>): AuthenticationProtocol() {

    var step = Step.INIT

    enum class Step {
        INIT,
        SENT_MY_DH_KEY,
        RECEIVED_PEER_DH_KEY,
        GENERATED_HANDSHAKE_SECRETS,
        SENT_HANDSHAKE_MESSAGE,
        RECEIVED_HANDSHAKE_MESSAGE,
        SESSION_ESTABLISHED
    }

    fun generateInitiatorHello(): InitiatorHelloMessage {
        transition(Step.INIT, Step.SENT_MY_DH_KEY)

        val keyPair = keyPairGenerator.generateKeyPair()
        myPrivateDHKey = keyPair.private
        myPublicDHKey = keyPair.public.encoded

        val commonHeader = CommonHeader(MessageType.INITIATOR_HELLO, PROTOCOL_VERSION, sessionId, 0, Instant.now().toEpochMilli())
        initiatorHelloMessage = InitiatorHelloMessage(commonHeader, myPublicDHKey!!, supportedModes)
        return initiatorHelloMessage!!
    }

    fun receiveResponderHello(responderHelloMsg: ResponderHelloMessage) {
        transition(Step.SENT_MY_DH_KEY, Step.RECEIVED_PEER_DH_KEY)

        responderHelloMessage = responderHelloMsg
        initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toBytes() + responderHelloMessage!!.toBytes()
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(responderHelloMsg.responderPublicKey))
        sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
    }

    fun generateHandshakeSecrets() {
        transition(Step.RECEIVED_PEER_DH_KEY, Step.GENERATED_HANDSHAKE_SECRETS)

        sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, initiatorHelloToResponderHelloBytes!!)
    }

    /**
     * Generates our handshake message.
     * Warning: the latency of this method is bounded by the latency of the provided [signingFn]. So, if you want to use this method from
     *          a performance-sensitive context, you should execute it asynchronously (i.e. in a separate thread)
     *          to avoid blocking any other processing.
     *
     * @param signingFn a callback function that will be invoked for performing signing (with the stable identity key).
     */
    fun generateOurHandshakeMessage(ourPublicKey: PublicKey,
                                    theirPublicKey: PublicKey,
                                    groupId: String,
                                    signingFn: (ByteArray) -> ByteArray): InitiatorHandshakeMessage {
        transition(Step.GENERATED_HANDSHAKE_SECRETS, Step.SENT_HANDSHAKE_MESSAGE)

        val initiatorRecordHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, PROTOCOL_VERSION,
                                                sessionId, 1, Instant.now().toEpochMilli())
        val initiatorRecordHeaderBytes = initiatorRecordHeader.toBytes()
        val groupIdBytes = groupId.toByteArray(Charsets.UTF_8)
        val initiatorEncryptedExtensions = messageDigest.hash(theirPublicKey.encoded) +
                                                     groupIdBytes.size.toByteArray() + groupIdBytes
        val initiatorParty = messageDigest.hash(ourPublicKey.encoded)
        val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorEncryptedExtensions + initiatorParty
        val initiatorPartyVerify = signingFn(INITIATOR_SIG_PAD.toByteArray(Charsets.UTF_8) +
                                             messageDigest.hash(initiatorHelloToResponderParty))
        val initiatorHelloToInitiatorPartyVerify = initiatorHelloToResponderParty + initiatorPartyVerify
        val initiatorFinished = hmac.calculateMac(sharedHandshakeSecrets!!.initiatorAuthKey,
                                                  messageDigest.hash(initiatorHelloToInitiatorPartyVerify))
        initiatorHandshakePayload = initiatorEncryptedExtensions +
                                 initiatorParty +
                                 (initiatorPartyVerify.size.toByteArray() + initiatorPartyVerify) +
                                 initiatorFinished

        val nonce = sharedHandshakeSecrets!!.initiatorNonce
        val (initiatorEncryptedData, initiatorTag) = aesCipher.encryptWithAssociatedData(initiatorRecordHeaderBytes,
                nonce, initiatorHandshakePayload!!, sharedHandshakeSecrets!!.initiatorEncryptionKey)
        return InitiatorHandshakeMessage(initiatorRecordHeader, initiatorEncryptedData, initiatorTag)
    }


    /**
     * @throws InvalidHandshakeResponderKeyHash if the responder sent a key hash that does not match with the key we were expecting.
     * @throws InvalidHandshakeMessageException if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     */
    fun validatePeerHandshakeMessage(responderHandshakeMessage: ResponderHandshakeMessage, theirPublicKey: PublicKey) {
        transition(Step.SENT_HANDSHAKE_MESSAGE, Step.RECEIVED_HANDSHAKE_MESSAGE)

        val responderRecordHeader = responderHandshakeMessage.recordHeader.toBytes()
        try {
            responderHandshakePayload = aesCipher.decrypt(responderRecordHeader,
                                                       responderHandshakeMessage.tag,
                                                       sharedHandshakeSecrets!!.responderNonce,
                                                       responderHandshakeMessage.encryptedData,
                                                       sharedHandshakeSecrets!!.responderEncryptionKey)
        } catch (e: AEADBadTagException) {
            throw InvalidHandshakeMessageException()
        }

        val responderHandshakeMessagePayloadDecryptedBuffer = ByteBuffer.wrap(responderHandshakePayload)
        val responderParty = ByteArray(messageDigest.digestLength)
        responderHandshakeMessagePayloadDecryptedBuffer.get(responderParty)
        if (!responderParty.contentEquals(messageDigest.hash(theirPublicKey.encoded))) {
            throw InvalidHandshakeResponderKeyHash()
        }
        val responderPartyVerifySize = responderHandshakeMessagePayloadDecryptedBuffer.int
        val responderPartyVerify = ByteArray(responderPartyVerifySize)
        responderHandshakeMessagePayloadDecryptedBuffer.get(responderPartyVerify)
        val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayload!! + responderParty
        val signatureWasValid = signature.verify(theirPublicKey,
                                            RESPONDER_SIG_PAD.toByteArray(Charsets.UTF_8) + messageDigest.hash(initiatorHelloToResponderParty),
                                                 responderPartyVerify)
        if (!signatureWasValid) {
            throw InvalidHandshakeMessageException()
        }

        val responderFinished = ByteArray(hmac.macLength)
        responderHandshakeMessagePayloadDecryptedBuffer.get(responderFinished)
        val initiatorHelloToResponderPartyVerify = initiatorHelloToResponderParty + responderPartyVerify
        val calculatedResponderFinished = hmac.calculateMac(sharedHandshakeSecrets!!.responderAuthKey,
                                                         messageDigest.hash(initiatorHelloToResponderPartyVerify))
        if (!calculatedResponderFinished.contentEquals(responderFinished)) {
            throw InvalidHandshakeMessageException()
        }
    }

    fun getSession(): AuthenticatedSession {
        transition(Step.RECEIVED_HANDSHAKE_MESSAGE, Step.SESSION_ESTABLISHED)

        val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayload!! + responderHandshakePayload!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return AuthenticatedSession(sessionId, 2, sharedSessionSecrets.initiatorEncryptionKey,
                                    sharedSessionSecrets.responderEncryptionKey)
    }

    private fun transition(fromStep: Step, toStep: Step) {
        if (step != fromStep) {
            throw IncorrectAPIUsageException("This method must be invoked when the protocol is in step $fromStep, but it was in step $step.")
        }

        step = toStep
    }

}

/**
 * Thrown when the responder sends an key hash that does not match the one we requested.
 */
class InvalidHandshakeResponderKeyHash: RuntimeException()