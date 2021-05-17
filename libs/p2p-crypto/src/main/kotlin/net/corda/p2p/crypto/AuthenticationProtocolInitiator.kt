package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.ClientHandshakeMessage
import net.corda.p2p.crypto.data.ClientHelloMessage
import net.corda.p2p.crypto.data.CommonHeader
import net.corda.p2p.crypto.data.ServerHandshakeMessage
import net.corda.p2p.crypto.data.ServerHelloMessage
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
 * - [generateClientHello]
 * - [receiveServerHello]
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

    fun generateClientHello(): ClientHelloMessage {
        require(step == Step.INIT)
        step = Step.SENT_MY_DH_KEY

        val keyPair = keyPairGenerator.generateKeyPair()
        myPrivateDHKey = keyPair.private
        myPublicDHKey = keyPair.public.encoded

        val commonHeader = CommonHeader(MessageType.CLIENT_HELLO, PROTOCOL_VERSION, sessionId, 0, Instant.now().toEpochMilli())
        clientHelloMessage = ClientHelloMessage(commonHeader, myPublicDHKey!!, supportedModes)
        return clientHelloMessage!!
    }

    fun receiveServerHello(serverHelloMsg: ServerHelloMessage) {
        require(step == Step.SENT_MY_DH_KEY)
        step = Step.RECEIVED_PEER_DH_KEY

        serverHelloMessage = serverHelloMsg
        clientHelloToServerHelloBytes = clientHelloMessage!!.toBytes() + serverHelloMessage!!.toBytes()
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(serverHelloMsg.serverPublicKey))
        sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
    }

    fun generateHandshakeSecrets() {
        require(step == Step.RECEIVED_PEER_DH_KEY)
        step = Step.GENERATED_HANDSHAKE_SECRETS

        sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, clientHelloToServerHelloBytes!!)
    }

    /**
     * @param signingFn a callback function that will be invoked for performing signing (with the stable identity key).
     */
    fun generateOurHandshakeMessage(ourPublicKey: PublicKey,
                                    theirPublicKey: PublicKey,
                                    groupId: String,
                                    signingFn: (ByteArray) -> ByteArray): ClientHandshakeMessage {
        require(step == Step.GENERATED_HANDSHAKE_SECRETS)
        step = Step.SENT_HANDSHAKE_MESSAGE

        val clientRecordHeader = CommonHeader(MessageType.CLIENT_HANDSHAKE, PROTOCOL_VERSION,
                                                sessionId, 1, Instant.now().toEpochMilli())
        val clientRecordHeaderBytes = clientRecordHeader.toBytes()
        val groupIdBytes = groupId.toByteArray(Charsets.UTF_8)
        val clientEncryptedExtensions = sha256Hash.hash(theirPublicKey.encoded) + groupIdBytes.size.toByteArray() + groupIdBytes
        val clientParty = sha256Hash.hash(ourPublicKey.encoded)
        val clientHelloToClientParty = clientHelloToServerHelloBytes!! + clientEncryptedExtensions + clientParty
        val clientPartyVerify = signingFn(clientSigPad.toByteArray(Charsets.UTF_8) + sha256Hash.hash(clientHelloToClientParty))
        val clientHelloToClientPartyVerify = clientHelloToClientParty + clientPartyVerify
        val clientFinished = hmac.calculateMac(sharedHandshakeSecrets!!.initiatorAuthKey, sha256Hash.hash(clientHelloToClientPartyVerify))
        clientHandshakePayload = clientEncryptedExtensions +
                                 clientParty +
                                 (clientPartyVerify.size.toByteArray() + clientPartyVerify) +
                                 clientFinished

        val nonce = sharedHandshakeSecrets!!.initiatorNonce
        val (clientEncryptedData, clientTag) = aesCipher.encryptWithAssociatedData(clientRecordHeaderBytes, nonce,
                clientHandshakePayload!!, sharedHandshakeSecrets!!.initiatorEncryptionKey)
        return ClientHandshakeMessage(clientRecordHeader, clientEncryptedData, clientTag)
    }


    /**
     * @throws InvalidHandshakeResponderKeyHash if the responder sent a key hash that does not match with the key we were expecting.
     * @throws InvalidHandshakeMessage if the handshake message was invalid (e.g. due to invalid signatures, MACs etc.)
     */
    fun validatePeerHandshakeMessage(serverHandshakeMessage: ServerHandshakeMessage, theirPublicKey: PublicKey) {
        require(step == Step.SENT_HANDSHAKE_MESSAGE)
        step = Step.RECEIVED_HANDSHAKE_MESSAGE

        val serverRecordHeader = serverHandshakeMessage.recordHeader.toBytes()
        try {
            serverHandshakePayload = aesCipher.decrypt(serverRecordHeader,
                                                       serverHandshakeMessage.tag,
                                                       sharedHandshakeSecrets!!.responderNonce,
                                                       serverHandshakeMessage.encryptedData,
                                                       sharedHandshakeSecrets!!.responderEncryptionKey)
        } catch (e: AEADBadTagException) {
            throw InvalidHandshakeMessage()
        }

        val responderHandshakeMessagePayloadDecryptedBuffer = ByteBuffer.wrap(serverHandshakePayload)
        val serverParty = ByteArray(sha256Hash.digestSize)
        responderHandshakeMessagePayloadDecryptedBuffer.get(serverParty)
        if (!serverParty.contentEquals(sha256Hash.hash(theirPublicKey.encoded))) {
            throw InvalidHandshakeResponderKeyHash()
        }
        val serverPartyVerifySize = responderHandshakeMessagePayloadDecryptedBuffer.int
        val serverPartyVerify = ByteArray(serverPartyVerifySize)
        responderHandshakeMessagePayloadDecryptedBuffer.get(serverPartyVerify)
        val clientHelloToServerParty = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + serverParty
        val signatureWasValid = signature.verify(theirPublicKey,
                                            serverSigPad.toByteArray(Charsets.UTF_8) + sha256Hash.hash(clientHelloToServerParty),
                                                 serverPartyVerify)
        if (!signatureWasValid) {
            throw InvalidHandshakeMessage()
        }

        val serverFinished = ByteArray(hmac.macLength)
        responderHandshakeMessagePayloadDecryptedBuffer.get(serverFinished)
        val clientHelloToServerPartyVerify = clientHelloToServerParty + serverPartyVerify
        val calculatedServerFinished = hmac.calculateMac(sharedHandshakeSecrets!!.responderAuthKey,
                                                         sha256Hash.hash(clientHelloToServerPartyVerify))
        if (!calculatedServerFinished.contentEquals(serverFinished)) {
            throw InvalidHandshakeMessage()
        }
    }

    fun getSession(): AuthenticatedSession {
        require(step == Step.RECEIVED_HANDSHAKE_MESSAGE)
        step = Step.SESSION_ESTABLISHED

        val fullTranscript = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + serverHandshakePayload!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return AuthenticatedSession(sessionId, 2, sharedSessionSecrets.initiatorEncryptionKey,
                                    sharedSessionSecrets.responderEncryptionKey)
    }

}

/**
 * Thrown when the responder sends an key hash that does not match the one we requested.
 */
class InvalidHandshakeResponderKeyHash: RuntimeException()