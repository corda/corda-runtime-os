package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.InitiatorHandshakeMessage
import net.corda.p2p.crypto.data.InitiatorHelloMessage
import net.corda.p2p.crypto.data.CommonHeader
import net.corda.p2p.crypto.data.ResponderHandshakeMessage
import net.corda.p2p.crypto.data.ResponderHelloMessage
import net.corda.p2p.crypto.util.calculateMac
import net.corda.p2p.crypto.util.decrypt
import net.corda.p2p.crypto.util.encryptWithAssociatedData
import net.corda.p2p.crypto.util.hash
import net.corda.p2p.crypto.util.perform
import net.corda.p2p.crypto.util.verify
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.AEADBadTagException

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
 * The [step] variable can be used to avoid calling methods when they have been called already (i.e. because of a duplicate message).
 *
 * This class is not thread-safe, which means clients that want to use it from different threads need to perform external synchronisation.
 */
class AuthenticationProtocolResponder(private val sessionId: String, private val supportedModes: List<Mode>): AuthenticationProtocol() {

    companion object {
        fun fromStep2(sessionId: String,
                      supportedModes: List<Mode>,
                      initiatorHelloMsg: InitiatorHelloMessage,
                      responderHelloMsg: ResponderHelloMessage,
                      privateDHKey: ByteArray,
                      publicDHKey: ByteArray): AuthenticationProtocolResponder {
            val protocol = AuthenticationProtocolResponder(sessionId, supportedModes)
            protocol.apply {
                receiveInitiatorHello(initiatorHelloMsg)
                myPrivateDHKey = protocol.ephemeralKeyFactory.generatePrivate(PKCS8EncodedKeySpec(privateDHKey))
                myPublicDHKey = publicDHKey

                sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
                responderHelloMessage = responderHelloMsg
                initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toBytes() + responderHelloMessage!!.toBytes()

                step = Step.SENT_MY_DH_KEY
            }

            return protocol
        }
    }

    var step = Step.INIT

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
        transition(Step.INIT, Step.RECEIVED_PEER_DH_KEY)

        initiatorHelloMessage = initiatorHelloMsg
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(initiatorHelloMsg.initiatorPublicKey))
    }

    /**
     * @throws NoCommonModeError when there is no mode that is supported by both the initiator and the responder.
     */
    fun generateResponderHello(): ResponderHelloMessage {
        transition(Step.RECEIVED_PEER_DH_KEY, Step.SENT_MY_DH_KEY)

        val keyPair = keyPairGenerator.generateKeyPair()
        myPrivateDHKey = keyPair.private
        myPublicDHKey = keyPair.public.encoded

        sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
        val commonHeader = CommonHeader(MessageType.RESPONDER_HELLO, PROTOCOL_VERSION, sessionId,
                             0, Instant.now().toEpochMilli())

        val commonModes = initiatorHelloMessage!!.supportedModes.intersect(supportedModes)
        val selectedMode = if (commonModes.isEmpty()) {
            throw NoCommonModeError(initiatorHelloMessage!!.supportedModes, supportedModes)
        } else {
            commonModes.first()
        }

        responderHelloMessage = ResponderHelloMessage(commonHeader, myPublicDHKey!!, selectedMode)
        initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toBytes() + responderHelloMessage!!.toBytes()
        return responderHelloMessage!!
    }

    /**
     * Caution: this is available in cases where one component needs to perform step 2 of the handshake
     * and forward the generated DH key downstream to another component that wil complete the protocol from that point on.
     * This means the private key will be temporarily exposed.
     *
     * That downstream component can resume the protocol from that point onwards
     * creating a new instance of this class using the [fromStep2] method.
     *
     * @return a pair containing (in that order) the private and the public DH key.
     */
    fun getDHKeyPair(): Pair<ByteArray, ByteArray> {
        checkState(Step.SENT_MY_DH_KEY)

        return myPrivateDHKey!!.encoded to myPublicDHKey!!
    }

    fun generateHandshakeSecrets() {
        transition(Step.SENT_MY_DH_KEY, Step.GENERATED_HANDSHAKE_SECRETS)

        sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, initiatorHelloToResponderHelloBytes!!)
    }

    /**
     * @param keyLookupFn a callback function used to perform a lookup of the initiator's public key given its SHA-256 hash.
     *
     * @throws InvalidHandshakeMessage if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     *
     * @return the SHA-256 of the public key we need to use in the handshake.
     *
     *
     */
    fun validatePeerHandshakeMessage(initiatorHandshakeMessage: InitiatorHandshakeMessage,
                                     keyLookupFn: (ByteArray) -> PublicKey): HandshakeIdentityData {
        transition(Step.GENERATED_HANDSHAKE_SECRETS, Step.RECEIVED_HANDSHAKE_MESSAGE)

        val initiatorRecordHeaderBytes = initiatorHandshakeMessage.recordHeader.toBytes()
        try {
            initiatorHandshakePayload = aesCipher.decrypt(initiatorRecordHeaderBytes,
                                                       initiatorHandshakeMessage.tag,
                                                       sharedHandshakeSecrets!!.initiatorNonce,
                                                       initiatorHandshakeMessage.encryptedData,
                                                       sharedHandshakeSecrets!!.initiatorEncryptionKey)
        } catch (e: AEADBadTagException) {
            throw InvalidHandshakeMessage()
        }
        val payloadBuffer = ByteBuffer.wrap(initiatorHandshakePayload)
        val responderPublicKeyHash = ByteArray(sha256Hash.digestSize)
        payloadBuffer.get(responderPublicKeyHash)
        val groupIdBytesSize = payloadBuffer.int
        val groupIdBytes = ByteArray(groupIdBytesSize)
        payloadBuffer.get(groupIdBytes)
        val groupId = groupIdBytes.toString(Charsets.UTF_8)
        val initiatorEncryptedExtensions = responderPublicKeyHash + groupIdBytesSize.toByteArray() + groupIdBytes
        val initiatorPublicKeyHash = ByteArray(sha256Hash.digestSize)
        payloadBuffer.get(initiatorPublicKeyHash)
        val initiatorPublicKey = keyLookupFn(initiatorPublicKeyHash)
        val initiatorPartyVerifySize = payloadBuffer.int
        val initiatorPartyVerify = ByteArray(initiatorPartyVerifySize)
        payloadBuffer.get(initiatorPartyVerify)
        val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorEncryptedExtensions + initiatorPublicKeyHash

        val signatureWasValid = signature.verify(initiatorPublicKey,
                                        initiatorSigPad.toByteArray(Charsets.UTF_8) + sha256Hash.hash(initiatorHelloToResponderParty),
                                             initiatorPartyVerify)
        if (!signatureWasValid) {
            throw InvalidHandshakeMessage()
        }

        val initiatorFinished = ByteArray(hmac.macLength)
        payloadBuffer.get(initiatorFinished)
        val initiatorHelloToResponderPartyVerify = initiatorHelloToResponderParty + initiatorPartyVerify

        val calculatedInitiatorFinished = hmac.calculateMac(sharedHandshakeSecrets!!.initiatorAuthKey,
                                                         sha256Hash.hash(initiatorHelloToResponderPartyVerify))
        if (!calculatedInitiatorFinished.contentEquals(initiatorFinished)) {
            throw InvalidHandshakeMessage()
        }

        return HandshakeIdentityData(initiatorPublicKeyHash, responderPublicKeyHash, groupId)
    }

    /**
     * @param signingFn a callback function that will be invoked for performing signing (with the stable identity key).
     */
    fun generateOurHandshakeMessage(ourPublicKey: PublicKey, signingFn: (ByteArray) -> ByteArray): ResponderHandshakeMessage {
        transition(Step.RECEIVED_HANDSHAKE_MESSAGE, Step.SENT_HANDSHAKE_MESSAGE)

        val responderRecordHeader = CommonHeader(MessageType.RESPONDER_HANDSHAKE, PROTOCOL_VERSION,
                                              sessionId, 1, Instant.now().toEpochMilli())
        val responderRecordHeaderBytes = responderRecordHeader.toBytes()
        val responderParty = sha256Hash.hash(ourPublicKey.encoded)
        val initiatorHelloToResponderParty = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayload!! + responderParty
        val responderPartyVerify = signingFn(responderSigPad.toByteArray(Charsets.UTF_8) + sha256Hash.hash(initiatorHelloToResponderParty))
        val initiatorHelloToResponderPartyVerify = initiatorHelloToResponderParty + responderPartyVerify
        val responderFinished = hmac.calculateMac(sharedHandshakeSecrets!!.responderAuthKey,
                                                  sha256Hash.hash(initiatorHelloToResponderPartyVerify))
        responderHandshakePayload = responderParty + (responderPartyVerify.size.toByteArray() + responderPartyVerify) + responderFinished
        val (responderEncryptedData, responderTag) = aesCipher.encryptWithAssociatedData(responderRecordHeaderBytes,
                sharedHandshakeSecrets!!.responderNonce, responderHandshakePayload!!, sharedHandshakeSecrets!!.responderEncryptionKey)
        return ResponderHandshakeMessage(responderRecordHeader, responderEncryptedData, responderTag)
    }

    fun getSession(): AuthenticatedSession {
        transition(Step.SENT_HANDSHAKE_MESSAGE, Step.SESSION_ESTABLISHED)

        val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayload!! + responderHandshakePayload!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return AuthenticatedSession(sessionId, 2, sharedSessionSecrets.responderEncryptionKey,
                                    sharedSessionSecrets.initiatorEncryptionKey)
    }

    private fun transition(fromStep: Step, toStep: Step) {
        checkState(fromStep)
        step = toStep
    }

    private fun checkState(expectedStep: Step) {
        if (step != expectedStep) {
            throw IncorrectAPIUsage("This method must be invoked when the protocol is in step $expectedStep, but it was in step $step.")
        }
    }

}

/**
 * Thrown when is no mode that is supported both by the initiator and the responder.
 */
class NoCommonModeError(val initiatorModes: List<Mode>, val responderModes: List<Mode>): RuntimeException()

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