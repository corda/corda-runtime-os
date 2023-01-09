package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorEncryptedExtensions
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.data.p2p.crypto.internal.InitiatorHandshakePayload
import net.corda.data.p2p.crypto.internal.ResponderHandshakePayload
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SIG_PAD
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.MIN_PACKET_SIZE
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SIG_PAD
import net.corda.p2p.crypto.util.calculateMac
import net.corda.p2p.crypto.util.decrypt
import net.corda.p2p.crypto.util.encryptWithAssociatedData
import net.corda.p2p.crypto.util.hash
import net.corda.p2p.crypto.util.perform
import net.corda.p2p.crypto.util.verify
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.AEADBadTagException

/**
 * The initiator side of the session authentication protocol.
 *
 * This class expects clients to call the following methods in sequence, i.e.:
 * - [generateInitiatorHello]
 * - [receiveResponderHello]
 * - [generateHandshakeSecrets]
 * - [generateOurHandshakeMessage]
 * - [validatePeerHandshakeMessage]
 * - [getSession]
 *
 * This class is idempotent. If a method is invoked multiple times, no side-effects will be executed and cached results will be returned.
 * This is in order to assist scenarios, where messages might be replayed safely without complicated logic on clients of the class.
 *
 * However, if methods are called out of sequence (e.g. [generateHandshakeSecrets] without the previous methods having ever been called),
 * this is an error and an [IncorrectAPIUsageException] will be thrown.
 *
 * This class is not thread-safe, which means clients that want to use it from different threads need to perform external synchronisation.
 */
@Suppress("LongParameterList")
class AuthenticationProtocolInitiator(val sessionId: String,
                                      private val supportedModes: Set<ProtocolMode>,
                                      private val ourMaxMessageSize: Int,
                                      private val ourPublicKey: PublicKey,
                                      private val groupId: String,
                                      private val certificateCheckMode: CertificateCheckMode
): AuthenticationProtocol(certificateCheckMode) {

    init {
        require(supportedModes.isNotEmpty()) { "At least one supported mode must be provided." }
        require(ourMaxMessageSize >= MIN_PACKET_SIZE ) { "max message size needs to be at least $MIN_PACKET_SIZE bytes." }
    }

    private var step = Step.INIT
    private var initiatorHandshakeMessage: InitiatorHandshakeMessage? = null
    private var session: Session? = null

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
        return transition(Step.INIT, Step.SENT_MY_DH_KEY, { initiatorHelloMessage!! }) {
            val keyPair = keyPairGenerator.generateKeyPair()
            myPrivateDHKey = keyPair.private
            myPublicDHKey = keyPair.public.encoded

            val commonHeader = CommonHeader(MessageType.INITIATOR_HELLO, PROTOCOL_VERSION, sessionId, 0, Instant.now().toEpochMilli())
            val identity = InitiatorHandshakeIdentity(ByteBuffer.wrap(messageDigest.hash(ourPublicKey.encoded)), groupId)
            initiatorHelloMessage = InitiatorHelloMessage(commonHeader, ByteBuffer.wrap(myPublicDHKey!!), supportedModes.toList(), identity)
            step = Step.SENT_MY_DH_KEY
            initiatorHelloMessage!!
        }
    }

    fun receiveResponderHello(responderHelloMsg: ResponderHelloMessage) {
        return transition(Step.SENT_MY_DH_KEY, Step.RECEIVED_PEER_DH_KEY, {}) {
            responderHelloMessage = responderHelloMsg
            selectedMode = responderHelloMsg.selectedMode
            if (!supportedModes.contains(selectedMode)) {
                throw InvalidSelectedModeError("The mode selected by the responder ($selectedMode) " +
                        "was not amongst the ones we proposed ($supportedModes).")
            }
            initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toByteBuffer().array() +
                    responderHelloMessage!!.toByteBuffer().array()
            peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(responderHelloMsg.responderPublicKey.array()))
            sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
        }
    }

    fun generateHandshakeSecrets() {
        return transition(Step.RECEIVED_PEER_DH_KEY, Step.GENERATED_HANDSHAKE_SECRETS, {}) {
            sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, initiatorHelloToResponderHelloBytes!!)
        }
    }

    /**
     * Generates our handshake message.
     * Warning: the latency of this method is bounded by the latency of the provided [signingFn]. So, if you want to use this method from
     *          a performance-sensitive context, you should execute it asynchronously (i.e. in a separate thread)
     *          to avoid blocking any other processing.
     *
     * @param signingFn a callback function that will be invoked for performing signing (with the stable identity key).
     */
    fun generateOurHandshakeMessage(theirPublicKey: PublicKey,
                                    ourCertificates: List<PemCertificate>?,
                                    signingFn: (ByteArray) -> ByteArray): InitiatorHandshakeMessage {
        return transition(Step.GENERATED_HANDSHAKE_SECRETS, Step.SENT_HANDSHAKE_MESSAGE, { initiatorHandshakeMessage!! }) {
            val initiatorRecordHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, PROTOCOL_VERSION,
                sessionId, 1, Instant.now().toEpochMilli())
            val initiatorRecordHeaderBytes = initiatorRecordHeader.toByteBuffer().array()
            val responderPublicKeyHash = ByteBuffer.wrap(messageDigest.hash(theirPublicKey.encoded))
            val initiatorHandshakePayload = InitiatorHandshakePayload(
                InitiatorEncryptedExtensions(responderPublicKeyHash, groupId, ourMaxMessageSize, ourCertificates),
                ByteBuffer.wrap(messageDigest.hash(ourPublicKey.encoded)),
                ByteBuffer.allocate(0),
                ByteBuffer.allocate(0)
            )

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
            initiatorHandshakeMessage = InitiatorHandshakeMessage(initiatorRecordHeader,
                ByteBuffer.wrap(initiatorEncryptedData), ByteBuffer.wrap(initiatorTag))
            initiatorHandshakeMessage!!
        }
    }


    /**
     * @throws InvalidHandshakeResponderKeyHash if the responder sent a key hash that does not match with the key we were expecting.
     * @throws InvalidHandshakeMessageException if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     */
    @Suppress("ThrowsCount")
    fun validatePeerHandshakeMessage(responderHandshakeMessage: ResponderHandshakeMessage,
                                     theirX500Name: MemberX500Name,
                                     theirPublicKey: PublicKey,
                                     theirSignatureSpec: SignatureSpec) {
        return transition(Step.SENT_HANDSHAKE_MESSAGE, Step.RECEIVED_HANDSHAKE_MESSAGE, {}) {
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
            val responderHandshakePayloadIncomplete = ResponderHandshakePayload(
                responderHandshakePayload.responderEncryptedExtensions,
                responderHandshakePayload.responderPublicKeyHash,
                ByteBuffer.allocate(0),
                ByteBuffer.allocate(0)
            )

            // check responder's public key hash matches requested one
            if (!responderHandshakePayload.responderPublicKeyHash.array().contentEquals(messageDigest.hash(theirPublicKey.encoded))) {
                throw InvalidHandshakeResponderKeyHash()
            }

            // validate signature
            val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! +
                    responderHandshakePayloadIncomplete.toByteBuffer().array()
            val signatureWasValid = getSignature(theirSignatureSpec).verify(theirPublicKey,
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

            responderHandshakePayload.responderEncryptedExtensions.maxMessageSize.apply {
                if (this > ourMaxMessageSize) {
                    throw InvalidMaxMessageSizeProposedError("Responder's proposed max message size ($this) " +
                            "was larger than the one we proposed ($ourMaxMessageSize).")
                }
                if (this < MIN_PACKET_SIZE) {
                    throw InvalidMaxMessageSizeProposedError("Responder's proposed max message size ($this) " +
                            "was smaller than the minimum allowed value ($MIN_PACKET_SIZE).")
                }
                agreedMaxMessageSize = this
            }
            validateCertificate(responderHandshakePayload, theirX500Name, theirPublicKey)
        }
    }

    private fun validateCertificate(
        responderHandshakePayload: ResponderHandshakePayload,
        theirX500Name: MemberX500Name,
        theirPublicKey: PublicKey,
    ) {
        if (certificateCheckMode != CertificateCheckMode.NoCertificate) {
            if (responderHandshakePayload.responderEncryptedExtensions.responderCertificate != null) {
                certificateValidator!!.validate(
                    responderHandshakePayload.responderEncryptedExtensions.responderCertificate,
                    theirX500Name,
                    theirPublicKey
                )
            } else {
                throw InvalidPeerCertificate("No peer certificate was sent in the responder handshake message.")
            }
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
        return transition(Step.RECEIVED_HANDSHAKE_MESSAGE, Step.SESSION_ESTABLISHED, { session!! }) {
            val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! + responderHandshakePayloadBytes!!
            val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
            session = when(selectedMode!!) {
                ProtocolMode.AUTHENTICATION_ONLY -> AuthenticatedSession(sessionId, 2, sharedSessionSecrets.initiatorEncryptionKey,
                    sharedSessionSecrets.responderEncryptionKey, agreedMaxMessageSize!!)
                ProtocolMode.AUTHENTICATED_ENCRYPTION -> AuthenticatedEncryptionSession(sessionId, 2,
                    sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.initiatorNonce,
                    sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.responderNonce,
                    agreedMaxMessageSize!!)
            }
            session!!
        }
    }

    private fun <R> transition(fromStep: Step, toStep: Step, getCachedValue: () -> R, calculateValue: () -> R): R {
        if (step < fromStep) {
            throw IncorrectAPIUsageException(
                "This method must be invoked when the protocol is at least in step $fromStep, but it was in step $step."
            )
        }
        if (step >= toStep) {
            return getCachedValue()
        }

        val value = calculateValue()
        step = toStep
        return value
    }

}

/**
 * Thrown when the responder sends a key hash that does not match the one we requested.
 */
class InvalidHandshakeResponderKeyHash: CordaRuntimeException("The responder sent a key hash that was different to the one we requested.")
class InvalidSelectedModeError(msg: String): CordaRuntimeException(msg)
