package net.corda.p2p.crypto.protocol.api

import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.internal.InitiatorEncryptedExtensions
import net.corda.p2p.crypto.internal.InitiatorHandshakePayload
import net.corda.p2p.crypto.internal.ResponderHandshakePayload
import net.corda.p2p.crypto.protocol.AuthenticationProtocol
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SIG_PAD
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SIG_PAD
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
class AuthenticationProtocolInitiator(private val sessionId: String,
                                      private val supportedModes: Set<ProtocolMode>): AuthenticationProtocol() {

    init {
        require(supportedModes.isNotEmpty()) { "At least one supported mode must be provided." }
    }

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
        initiatorHelloMessage = InitiatorHelloMessage(commonHeader, ByteBuffer.wrap(myPublicDHKey!!) , supportedModes.toList())
        return initiatorHelloMessage!!
    }

    fun receiveResponderHello(responderHelloMsg: ResponderHelloMessage) {
        transition(Step.SENT_MY_DH_KEY, Step.RECEIVED_PEER_DH_KEY)

        responderHelloMessage = responderHelloMsg
        selectedMode = responderHelloMsg.selectedMode
        initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toByteBuffer().array() +
                                              responderHelloMessage!!.toByteBuffer().array()
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(responderHelloMsg.responderPublicKey.array()))
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
        val initiatorRecordHeaderBytes = initiatorRecordHeader.toByteBuffer().array()
        val initiatorHandshakePayload = InitiatorHandshakePayload()
        val responderPublicKeyHash = ByteBuffer.wrap(messageDigest.hash(theirPublicKey.encoded))
        initiatorHandshakePayload.initiatorEncryptedExtensions = InitiatorEncryptedExtensions(responderPublicKeyHash, groupId)
        initiatorHandshakePayload.initiatorPublicKeyHash = ByteBuffer.wrap(messageDigest.hash(ourPublicKey.encoded))
        initiatorHandshakePayload.initiatorPartyVerify = ByteBuffer.allocate(0)
        initiatorHandshakePayload.initiatorFinished = ByteBuffer.allocate(0)

        // calculate signature
        val initiatorHelloToInitiatorPublicKeyHash = initiatorHelloToResponderHelloBytes!! +
                                                      initiatorHandshakePayload.toByteBuffer().array()
        initiatorHandshakePayload.initiatorPartyVerify = ByteBuffer.wrap(signingFn(INITIATOR_SIG_PAD.toByteArray(Charsets.UTF_8) +
                                                                            messageDigest.hash(initiatorHelloToInitiatorPublicKeyHash)))

        // calculate MAC
        val initiatorHelloToInitiatorPartyVerify = initiatorHelloToResponderHelloBytes!! +
                                                            initiatorHandshakePayload.toByteBuffer().array()
        initiatorHandshakePayload.initiatorFinished = ByteBuffer.wrap(hmac.calculateMac(sharedHandshakeSecrets!!.initiatorAuthKey,
                                                                                messageDigest.hash(initiatorHelloToInitiatorPartyVerify)))
        initiatorHandshakePayloadBytes = initiatorHandshakePayload.toByteBuffer().array()

        // encrypt payload
        val nonce = sharedHandshakeSecrets!!.initiatorNonce
        val (initiatorEncryptedData, initiatorTag) = aesCipher.encryptWithAssociatedData(initiatorRecordHeaderBytes,
                nonce, initiatorHandshakePayloadBytes!!, sharedHandshakeSecrets!!.initiatorEncryptionKey)
        return InitiatorHandshakeMessage(initiatorRecordHeader, ByteBuffer.wrap(initiatorEncryptedData), ByteBuffer.wrap(initiatorTag))
    }


    /**
     * @throws InvalidHandshakeResponderKeyHash if the responder sent a key hash that does not match with the key we were expecting.
     * @throws InvalidHandshakeMessageException if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     */
    fun validatePeerHandshakeMessage(responderHandshakeMessage: ResponderHandshakeMessage, theirPublicKey: PublicKey) {
        transition(Step.SENT_HANDSHAKE_MESSAGE, Step.RECEIVED_HANDSHAKE_MESSAGE)

        val responderRecordHeader = responderHandshakeMessage.header.toByteBuffer().array()
        try {
            responderHandshakePayloadBytes = aesCipher.decrypt(responderRecordHeader,
                                                       responderHandshakeMessage.authTag.array(),
                                                       sharedHandshakeSecrets!!.responderNonce,
                                                       responderHandshakeMessage.encryptedData.array(),
                                                       sharedHandshakeSecrets!!.responderEncryptionKey)
        } catch (e: AEADBadTagException) {
            throw InvalidHandshakeMessageException()
        }

        val responderHandshakePayload = ResponderHandshakePayload.fromByteBuffer(ByteBuffer.wrap(responderHandshakePayloadBytes))
        val responderHandshakePayloadIncomplete = ResponderHandshakePayload()
        responderHandshakePayloadIncomplete.responderPublicKeyHash = responderHandshakePayload.responderPublicKeyHash
        responderHandshakePayloadIncomplete.responderPartyVerify = ByteBuffer.allocate(0)
        responderHandshakePayloadIncomplete.responderFinished = ByteBuffer.allocate(0)

        // check responder's public key hash matches requested one
        if (!responderHandshakePayload.responderPublicKeyHash.array().contentEquals(messageDigest.hash(theirPublicKey.encoded))) {
            throw InvalidHandshakeResponderKeyHash()
        }

        // validate signature
        val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! +
                                                      responderHandshakePayloadIncomplete.toByteBuffer().array()
        val signatureWasValid = signature.verify(theirPublicKey,
                                            RESPONDER_SIG_PAD.toByteArray(Charsets.UTF_8) + messageDigest.hash(initiatorHelloToResponderParty),
                                                 responderHandshakePayload.responderPartyVerify.array())
        if (!signatureWasValid) {
            throw InvalidHandshakeMessageException()
        }
        responderHandshakePayloadIncomplete.responderPartyVerify = responderHandshakePayload.responderPartyVerify

        // validate MAC
        val initiatorHelloToResponderPartyVerify = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! +
                                                            responderHandshakePayloadIncomplete.toByteBuffer().array()
        val calculatedResponderFinished = hmac.calculateMac(sharedHandshakeSecrets!!.responderAuthKey,
                                                         messageDigest.hash(initiatorHelloToResponderPartyVerify))
        if (!calculatedResponderFinished.contentEquals(responderHandshakePayload.responderFinished.array())) {
            throw InvalidHandshakeMessageException()
        }
    }

    /**
     * Returns the established session.
     * The concrete type of the session will depend on the negotiated protocol mode between the two parties.
     *
     * If the selected mode was [ProtocolMode.AUTHENTICATION_ONLY], this will return a [AuthenticatedSession].
     * If the selected mode was [ProtocolMode.AUTHENTICATED_ENCRYPTION], this will return a [AuthenticatedEncryptionSession].
     */
    fun getSession(): Session {
        transition(Step.RECEIVED_HANDSHAKE_MESSAGE, Step.SESSION_ESTABLISHED)

        val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! + responderHandshakePayloadBytes!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return when(selectedMode!!) {
            ProtocolMode.AUTHENTICATION_ONLY -> AuthenticatedSession(sessionId, 2,
                                                sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.responderEncryptionKey)
            ProtocolMode.AUTHENTICATED_ENCRYPTION -> AuthenticatedEncryptionSession(sessionId, 2,
                                                        sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.initiatorNonce,
                                                        sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.responderNonce)
        }
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