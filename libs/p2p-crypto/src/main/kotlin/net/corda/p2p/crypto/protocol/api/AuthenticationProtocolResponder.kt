package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.internal.InitiatorHandshakeIdentity
import net.corda.data.p2p.crypto.internal.InitiatorHandshakePayload
import net.corda.data.p2p.crypto.internal.ResponderEncryptedExtensions
import net.corda.data.p2p.crypto.internal.ResponderHandshakePayload
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SIG_PAD
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.MIN_PACKET_SIZE
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.PROTOCOL_VERSION
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SIG_PAD
import net.corda.p2p.crypto.protocol.ProtocolModeNegotiation
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
import kotlin.math.min

/**
 * The responder side of the session authentication protocol.
 *
 * This class expects clients to call methods for each step in sequence and only once, i.e.:
 * - [receiveInitiatorHello]
 * - [generateResponderHello]
 * - [generateHandshakeSecrets]
 * - [validatePeerHandshakeMessage]
 * - [generateOurHandshakeMessage]
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
class AuthenticationProtocolResponder(val sessionId: String,
                                      private val supportedModes: Set<ProtocolMode>,
                                      private val ourMaxMessageSize: Int,
                                      private val certificateCheckMode: CertificateCheckMode
): AuthenticationProtocol(certificateCheckMode) {

    init {
        require(supportedModes.isNotEmpty()) { "At least one supported mode must be provided." }
        require(ourMaxMessageSize >= MIN_PACKET_SIZE) { "max message size needs to be at least $MIN_PACKET_SIZE bytes." }
    }

    private var step = Step.INIT
    private var handshakeIdentityData: HandshakeIdentityData? = null
    private var responderHandshakeMessage: ResponderHandshakeMessage? = null
    private var session: Session? = null

    enum class Step {
        INIT,
        RECEIVED_PEER_DH_KEY,
        SENT_MY_DH_KEY,
        GENERATED_HANDSHAKE_SECRETS,
        RECEIVED_HANDSHAKE_MESSAGE,
        SENT_HANDSHAKE_MESSAGE,
        SESSION_ESTABLISHED
    }

    fun receiveInitiatorHello(initiatorHelloMsg: InitiatorHelloMessage) {
        return transition(Step.INIT, Step.RECEIVED_PEER_DH_KEY, {}) {
            initiatorHelloMessage = initiatorHelloMsg
            peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(initiatorHelloMsg.initiatorPublicKey.array()))
        }
    }

    /**
     * Get identity information (SHA-256 hash of the identity public key and group identity) about the Initiator.
     * That should not be called before [receiveInitiatorHello]. In this case, it will throw an [IncorrectAPIUsageException].
     */
    fun getInitiatorIdentity(): InitiatorHandshakeIdentity {
        if(step < Step.RECEIVED_PEER_DH_KEY) {
            throw IncorrectAPIUsageException("getInitiatorIdentity cannot be invoked before processing an initiator hello.")
        }
        return initiatorHelloMessage!!.source
    }

    /**
     * @throws NoCommonModeError when there is no mode that is supported by both the initiator and the responder.
     */
    fun generateResponderHello(): ResponderHelloMessage {
        return transition(Step.RECEIVED_PEER_DH_KEY, Step.SENT_MY_DH_KEY, { responderHelloMessage!! }) {
            val keyPair = keyPairGenerator.generateKeyPair()
            myPrivateDHKey = keyPair.private
            myPublicDHKey = keyPair.public.encoded

            sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
            val commonHeader = CommonHeader(MessageType.RESPONDER_HELLO, PROTOCOL_VERSION, sessionId,
                0, Instant.now().toEpochMilli())

            selectedMode = ProtocolModeNegotiation.selectMode(initiatorHelloMessage!!.supportedModes.toSet(), supportedModes)
            responderHelloMessage = ResponderHelloMessage(commonHeader, ByteBuffer.wrap(myPublicDHKey!!), selectedMode)
            initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toByteBuffer().array() +
                    responderHelloMessage!!.toByteBuffer().array()
            responderHelloMessage!!
        }
    }

    fun generateHandshakeSecrets() {
        return transition(Step.SENT_MY_DH_KEY, Step.GENERATED_HANDSHAKE_SECRETS, {}) {
            sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, initiatorHelloToResponderHelloBytes!!)
        }
    }

    /**
     * Validates the handshake message from the peer.
     *
     * @param initiatorPublicKey the public key used to validate the handshake message.
     * @throws InvalidHandshakeMessageException if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     *
     * @return the SHA-256 of the public key we need to use in the handshake.
     *
     *
     */
    @Suppress("ThrowsCount")
    fun validatePeerHandshakeMessage(
        initiatorHandshakeMessage: InitiatorHandshakeMessage,
        initiatorX500Name: MemberX500Name,
        initiatorPublicKey: PublicKey,
        initiatorSignatureSpec: SignatureSpec,
    ): HandshakeIdentityData {
        return transition(Step.GENERATED_HANDSHAKE_SECRETS, Step.RECEIVED_HANDSHAKE_MESSAGE, { handshakeIdentityData!! }) {
            val initiatorPublicKeyHash = messageDigest.hash(initiatorPublicKey.encoded)
            val expectedInitiatorPublicKeyHash = getInitiatorIdentity().initiatorPublicKeyHash.array()
            if (!initiatorPublicKeyHash.contentEquals(expectedInitiatorPublicKeyHash)) {
                throw WrongPublicKeyHashException(expectedInitiatorPublicKeyHash, initiatorPublicKeyHash)
            }

            val initiatorRecordHeaderBytes = initiatorHandshakeMessage.header.toByteBuffer().array()
            try {
                initiatorHandshakePayloadBytes = aesCipher.decrypt(initiatorRecordHeaderBytes,
                    initiatorHandshakeMessage.authTag.array(),
                    sharedHandshakeSecrets!!.initiatorNonce,
                    initiatorHandshakeMessage.encryptedData.array(),
                    sharedHandshakeSecrets!!.initiatorEncryptionKey)
            } catch (e: AEADBadTagException) {
                throw InvalidHandshakeMessageException()
            }

            val initiatorHandshakePayload = InitiatorHandshakePayload.fromByteBuffer(ByteBuffer.wrap(initiatorHandshakePayloadBytes))
            val initiatorHandshakePayloadIncomplete = InitiatorHandshakePayload(
                initiatorHandshakePayload.initiatorEncryptedExtensions,
                initiatorHandshakePayload.initiatorPublicKeyHash,
                ByteBuffer.allocate(0),
                ByteBuffer.allocate(0)
            )

            // validate signature
            val initiatorHelloToInitiatorPublicKeyHash = initiatorHelloToResponderHelloBytes!! +
                    initiatorHandshakePayloadIncomplete.toByteBuffer().array()
            val signatureWasValid = getSignature(initiatorSignatureSpec).verify(initiatorPublicKey,
                INITIATOR_SIG_PAD.toByteArray(Charsets.UTF_8) +
                        messageDigest.hash(initiatorHelloToInitiatorPublicKeyHash),
                initiatorHandshakePayload.initiatorPartyVerify.array())
            if (!signatureWasValid) {
                throw InvalidHandshakeMessageException()
            }
            initiatorHandshakePayloadIncomplete.initiatorPartyVerify = initiatorHandshakePayload.initiatorPartyVerify

            // validate MAC
            val initiatorHelloToInitiatorPartyVerify = initiatorHelloToResponderHelloBytes!! +
                    initiatorHandshakePayloadIncomplete.toByteBuffer().array()
            val calculatedInitiatorFinished = hmac.calculateMac(sharedHandshakeSecrets!!.initiatorAuthKey,
                messageDigest.hash(initiatorHelloToInitiatorPartyVerify))
            if (!calculatedInitiatorFinished.contentEquals(initiatorHandshakePayload.initiatorFinished.array())) {
                throw InvalidHandshakeMessageException()
            }

            initiatorHandshakePayload.initiatorEncryptedExtensions.maxMessageSize.apply {
                if (this < MIN_PACKET_SIZE) {
                    throw InvalidMaxMessageSizeProposedError("Initiator's proposed max message size ($this) " +
                            "was smaller than the minimum allowed value ($MIN_PACKET_SIZE).")
                }

                agreedMaxMessageSize = min(ourMaxMessageSize, this)
            }
            validateCertificate(initiatorHandshakePayload, initiatorX500Name, initiatorPublicKey)

            handshakeIdentityData =  HandshakeIdentityData(initiatorHandshakePayload.initiatorPublicKeyHash.array(),
                initiatorHandshakePayload.initiatorEncryptedExtensions.responderPublicKeyHash.array(),
                initiatorHandshakePayload.initiatorEncryptedExtensions.groupId)
            handshakeIdentityData!!
        }
    }

    private fun validateCertificate(
        initiatorHandshakePayload: InitiatorHandshakePayload,
        initiatorX500Name: MemberX500Name,
        initiatorPublicKey: PublicKey
    ) {
        if (certificateCheckMode != CertificateCheckMode.NoCertificate) {
            if (initiatorHandshakePayload.initiatorEncryptedExtensions.initiatorCertificate != null) {
                certificateValidator!!.validate(
                    initiatorHandshakePayload.initiatorEncryptedExtensions.initiatorCertificate,
                    initiatorX500Name,
                    initiatorPublicKey
                )
            } else {
                throw InvalidPeerCertificate("No peer certificate was sent in the initiator handshake message.")
            }
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
    fun generateOurHandshakeMessage(
        ourPublicKey: PublicKey,
        ourCertificates: List<PemCertificate>?,
        signingFn: (ByteArray) -> ByteArray,
    ): ResponderHandshakeMessage {
        return transition(Step.RECEIVED_HANDSHAKE_MESSAGE, Step.SENT_HANDSHAKE_MESSAGE, { responderHandshakeMessage!! }) {
            val responderRecordHeader = CommonHeader(MessageType.RESPONDER_HANDSHAKE, PROTOCOL_VERSION,
                sessionId, 1, Instant.now().toEpochMilli())
            val responderRecordHeaderBytes = responderRecordHeader.toByteBuffer().array()
            val responderHandshakePayload = ResponderHandshakePayload(
                ResponderEncryptedExtensions(agreedMaxMessageSize, ourCertificates),
                ByteBuffer.wrap(messageDigest.hash(ourPublicKey.encoded)),
                ByteBuffer.allocate(0),
                ByteBuffer.allocate(0)
            )

            // calculate signature
            val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! +
                    responderHandshakePayload.toByteBuffer().array()
            responderHandshakePayload.responderPartyVerify = ByteBuffer.wrap(signingFn(RESPONDER_SIG_PAD.toByteArray(Charsets.UTF_8) +
                    messageDigest.hash(initiatorHelloToResponderParty)))

            // calculate MAC
            val initiatorHelloToResponderPartyVerify = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! +
                    responderHandshakePayload.toByteBuffer().array()
            responderHandshakePayload.responderFinished = ByteBuffer.wrap(hmac.calculateMac(sharedHandshakeSecrets!!.responderAuthKey,
                messageDigest.hash(initiatorHelloToResponderPartyVerify)))

            responderHandshakePayloadBytes = responderHandshakePayload.toByteBuffer().array()
            val (responderEncryptedData, responderTag) = aesCipher.encryptWithAssociatedData(responderRecordHeaderBytes,
                sharedHandshakeSecrets!!.responderNonce, responderHandshakePayloadBytes!!, sharedHandshakeSecrets!!.responderEncryptionKey)
            responderHandshakeMessage = ResponderHandshakeMessage(
                responderRecordHeader,
                ByteBuffer.wrap(responderEncryptedData),
                ByteBuffer.wrap(responderTag)
            )
            responderHandshakeMessage!!
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
        return transition(Step.SENT_HANDSHAKE_MESSAGE, Step.SESSION_ESTABLISHED, { session!! }) {
            val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! + responderHandshakePayloadBytes!!
            val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)

            session = when(selectedMode!!) {
                ProtocolMode.AUTHENTICATION_ONLY -> AuthenticatedSession(sessionId, 2, sharedSessionSecrets.responderEncryptionKey,
                    sharedSessionSecrets.initiatorEncryptionKey, agreedMaxMessageSize!!)
                ProtocolMode.AUTHENTICATED_ENCRYPTION -> AuthenticatedEncryptionSession(sessionId, 2,
                    sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.responderNonce,
                    sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.initiatorNonce,
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
 * Thrown when is no mode that is supported both by the initiator and the responder.
 */
class NoCommonModeError(initiatorModes: Set<ProtocolMode>, responderModes: Set<ProtocolMode>):
    CordaRuntimeException("There was no common mode between those supported by the initiator ($initiatorModes) " +
                          "and those supported by the responder ($responderModes).")

/**
 * @property initiatorPublicKeyHash the SHA-256 hash of the initiator's public key.
 * @property responderPublicKeyHash the SHA-256 hash of the public key to be used by the responder.
 * @property groupId the group identifier the two identities are part of.
 */
data class HandshakeIdentityData(val initiatorPublicKeyHash: ByteArray, val responderPublicKeyHash: ByteArray, val groupId: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HandshakeIdentityData

        if (!initiatorPublicKeyHash.contentEquals(other.initiatorPublicKeyHash)) return false
        if (!responderPublicKeyHash.contentEquals(other.responderPublicKeyHash)) return false
        if (groupId != other.groupId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initiatorPublicKeyHash.contentHashCode()
        result = 31 * result + responderPublicKeyHash.contentHashCode()
        result = 31 * result + groupId.hashCode()
        return result
    }
}
