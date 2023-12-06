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
import net.corda.data.p2p.crypto.protocol.AuthenticatedEncryptionSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticatedSessionDetails
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolHeader
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolInitiatorDetails
import net.corda.data.p2p.crypto.protocol.CheckCertificate
import net.corda.data.p2p.crypto.protocol.InitiatorStep
import net.corda.data.p2p.crypto.protocol.Session
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
import net.corda.utilities.crypto.publicKeyFactory
import net.corda.utilities.crypto.toPem

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
class AuthenticationProtocolInitiator(
    val details: AuthenticationProtocolInitiatorDetails,
    private val revocationChecker: RevocationChecker,
    certificateValidatorFactory: CertificateValidatorFactory = CertificateValidatorFactory.Default,
): AuthenticationProtocolWrapper(details.header, certificateValidatorFactory) {
    companion object {
        @Suppress("LongParameterList")
        fun create(
            sessionId: String,
            supportedModes: Collection<ProtocolMode>,
            ourMaxMessageSize: Int,
            ourPublicKey: PublicKey,
            groupId: String,
            mode: CertificateCheckMode,
            revocationChecker: RevocationChecker,
            certificateValidatorFactory: CertificateValidatorFactory = CertificateValidatorFactory.Default,
        ): AuthenticationProtocolInitiator {
            val header = AuthenticationProtocolHeader(
                sessionId,
                ourMaxMessageSize,
                null,
            )
            val certificateCheckMode = when (mode) {
                is CertificateCheckMode.CheckCertificate -> CheckCertificate(
                    mode.truststore,
                    mode.revocationCheckMode,
                )

                CertificateCheckMode.NoCertificate -> null
            }
            val details = AuthenticationProtocolInitiatorDetails(
                header,
                InitiatorStep.INIT,
                supportedModes.toList(),
                ourPublicKey.toPem(),
                groupId,
                certificateCheckMode,
                null,
            )
            return AuthenticationProtocolInitiator(
                details,
                revocationChecker,
                certificateValidatorFactory,
            )
        }
    }

    private val ourPublicKey by lazy {
        publicKeyFactory(details.ourPublicKey.reader()) ?: throw CordaRuntimeException("Invalid public key PEM")
    }
    private val certificateCheckMode by lazy {
        details.certificateCheckMode?.let {
            CertificateCheckMode.CheckCertificate(
                truststore = it.truststore,
                revocationCheckMode = it.revocationCheckMode,
            )
        } ?: CertificateCheckMode.NoCertificate
    }
    private val supportedModes by lazy {
        details.supportedModes.toSet()
    }
    private var session: SessionWrapper? = null

    init {
        require(details.supportedModes.isNotEmpty()) { "At least one supported mode must be provided." }
        require(header.ourMaxMessageSize >= MIN_PACKET_SIZE ) { "max message size needs to be at least $MIN_PACKET_SIZE bytes." }
    }

    fun generateInitiatorHello(): InitiatorHelloMessage {
        return transition(InitiatorStep.INIT, InitiatorStep.SENT_MY_DH_KEY, { initiatorHelloMessage!! }) {
            val keyPair = keyPairGenerator.generateKeyPair()
            myPrivateDHKey = keyPair.private
            myPublicDHKey = keyPair.public.encoded

            val commonHeader = CommonHeader(
                MessageType.INITIATOR_HELLO,
                PROTOCOL_VERSION,
                sessionId,
                0,
                Instant.now().toEpochMilli()
            )
            val identity = InitiatorHandshakeIdentity(ByteBuffer.wrap(hash(ourPublicKey)), details.groupId)
            initiatorHelloMessage = InitiatorHelloMessage(commonHeader, ByteBuffer.wrap(myPublicDHKey!!), identity)
            details.step = InitiatorStep.SENT_MY_DH_KEY
            initiatorHelloMessage!!
        }
    }

    fun receiveResponderHello(responderHelloMsg: ResponderHelloMessage) {
        return transition(InitiatorStep.SENT_MY_DH_KEY, InitiatorStep.RECEIVED_PEER_DH_KEY, {}) {
            responderHelloMessage = responderHelloMsg
            initiatorHelloToResponderHelloBytes = initiatorHelloMessage!!.toByteBuffer().array() +
                    responderHelloMessage!!.toByteBuffer().array()
            peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(responderHelloMsg.responderPublicKey.array()))
            sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
        }
    }

    fun generateHandshakeSecrets() {
        return transition(InitiatorStep.RECEIVED_PEER_DH_KEY, InitiatorStep.GENERATED_HANDSHAKE_SECRETS, {}) {
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
        return transition(
            InitiatorStep.GENERATED_HANDSHAKE_SECRETS,
            InitiatorStep.SENT_HANDSHAKE_MESSAGE,
            { details.initiatorHandshakeMessage!! },
        ) {
            val initiatorRecordHeader = CommonHeader(MessageType.INITIATOR_HANDSHAKE, PROTOCOL_VERSION,
                sessionId, 1, Instant.now().toEpochMilli())
            val initiatorRecordHeaderBytes = initiatorRecordHeader.toByteBuffer().array()
            val responderPublicKeyHash = ByteBuffer.wrap(hash(theirPublicKey))
            val initiatorHandshakePayload = InitiatorHandshakePayload(
                InitiatorEncryptedExtensions(
                    responderPublicKeyHash,
                    details.groupId,
                    ourMaxMessageSize,
                    ourCertificates,
                    details.supportedModes,
                ),
                ByteBuffer.wrap(hash(ourPublicKey)),
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
            InitiatorHandshakeMessage(initiatorRecordHeader,
                ByteBuffer.wrap(initiatorEncryptedData), ByteBuffer.wrap(initiatorTag)).also {
                details.initiatorHandshakeMessage = it
            }
        }
    }


    /**
     * @throws InvalidHandshakeResponderKeyHash if the responder sent a key hash that does not match with the key we were expecting.
     * @throws InvalidHandshakeMessageException if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     */
    @Suppress("ThrowsCount")
    fun validatePeerHandshakeMessage(
        responderHandshakeMessage: ResponderHandshakeMessage,
        theirX500Name: MemberX500Name,
        theirPublicKeys: Collection<Pair<PublicKey, SignatureSpec>>,
    ) {
        return transition(InitiatorStep.SENT_HANDSHAKE_MESSAGE, InitiatorStep.RECEIVED_HANDSHAKE_MESSAGE, {}) {
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

            // Find the correct key
            val (theirPublicKey, theirSignatureSpec) = theirPublicKeys.firstOrNull { (key, _) ->
                responderHandshakePayload.responderPublicKeyHash.array().contentEquals(hash(key))
            } ?: throw InvalidHandshakeResponderKeyHash()

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
            selectedMode = responderHandshakePayload.responderEncryptedExtensions.selectedMode
            if (!supportedModes.contains(selectedMode)) {
                throw InvalidSelectedModeError("The mode selected by the responder ($selectedMode) " +
                        "was not amongst the ones we proposed ($supportedModes).")
            }
            validateCertificate(
                certificateCheckMode,
                responderHandshakePayload.responderEncryptedExtensions.responderCertificate,
                theirX500Name,
                theirPublicKey,
                "responder handshake message",
                revocationChecker,
            )
        }
    }

    /**
     * Returns the established session.
     * The concrete type of the session will depend on the negotiated protocol mode between the two parties.
     *
     * If the selected mode was [ProtocolMode.AUTHENTICATION_ONLY], this will return a [AuthenticatedSession].
     * If the selected mode was [ProtocolMode.AUTHENTICATED_ENCRYPTION], this will return a [AuthenticatedEncryptionSession].
     */
    fun getSession(): SessionWrapper {
        return transition(InitiatorStep.RECEIVED_HANDSHAKE_MESSAGE, InitiatorStep.SESSION_ESTABLISHED, { session!! }) {
            val fullTranscript = initiatorHelloToResponderHelloBytes!! + initiatorHandshakePayloadBytes!! + responderHandshakePayloadBytes!!
            val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
            val session = when(selectedMode!!) {
                ProtocolMode.AUTHENTICATION_ONLY -> {
                    val sessionDetails = AuthenticatedSessionDetails(
                        sharedSessionSecrets.initiatorEncryptionKey.toData(),
                        sharedSessionSecrets.responderEncryptionKey.toData(),
                    )
                    val session = Session(
                        sessionId,
                        ourMaxMessageSize,
                        sessionDetails
                    )
                    this.header.session = session
                    AuthenticatedSession(session, sessionDetails)
                }
                ProtocolMode.AUTHENTICATED_ENCRYPTION -> {
                    val sessionDetails = AuthenticatedEncryptionSessionDetails(
                        sharedSessionSecrets.initiatorEncryptionKey.toData(),
                        ByteBuffer.wrap(sharedSessionSecrets.initiatorNonce),
                        sharedSessionSecrets.responderEncryptionKey.toData(),
                        ByteBuffer.wrap(sharedSessionSecrets.responderNonce),
                    )
                    val session = Session(
                        sessionId,
                        ourMaxMessageSize,
                        sessionDetails
                    )
                    this.header.session = session
                    AuthenticatedEncryptionSession(session, sessionDetails)
                }
            }
            this.session = session
            session
        }
    }

    private fun <R> transition(fromStep: InitiatorStep, toStep: InitiatorStep, getCachedValue: () -> R, calculateValue: () -> R): R {
        if (details.step < fromStep) {
            throw IncorrectAPIUsageException(
                "This method must be invoked when the protocol is at least in step $fromStep, but it was in step ${details.step}."
            )
        }
        if (details.step >= toStep) {
            return getCachedValue()
        }

        val value = calculateValue()
        details.step = toStep
        return value
    }

}

/**
 * Thrown when the responder sends a key hash that does not match the one we requested.
 */
class InvalidHandshakeResponderKeyHash: CordaRuntimeException("The responder sent a key hash that was different to the one we requested.")
class InvalidSelectedModeError(msg: String): CordaRuntimeException(msg)
