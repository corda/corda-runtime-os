package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.*
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

/**
 * The responder side of the session authentication protocol.
 *
 * This class expects clients to call methods for each step in sequence and only once, i.e.:
 * - [receiveClientHello]
 * - [generateServerHello]
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
        fun fromStep2(sessionId: String, supportedModes: List<Mode>, clientHelloMsg: ClientHelloMessage, serverHelloMsg: ServerHelloMessage, privateDHKey: ByteArray, publicDHKey: ByteArray): AuthenticationProtocolResponder {
            val protocol = AuthenticationProtocolResponder(sessionId, supportedModes)
            protocol.apply {
                receiveClientHello(clientHelloMsg)
                myPrivateDHKey = protocol.ephemeralKeyFactory.generatePrivate(PKCS8EncodedKeySpec(privateDHKey))
                myPublicDHKey = publicDHKey

                sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
                serverHelloMessage = serverHelloMsg
                clientHelloToServerHelloBytes = clientHelloMessage!!.toBytes() + serverHelloMessage!!.toBytes()

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

    fun receiveClientHello(clientHelloMsg: ClientHelloMessage) {
        require(step == Step.INIT)
        step = Step.RECEIVED_PEER_DH_KEY

        clientHelloMessage = clientHelloMsg
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(clientHelloMsg.clientPublicKey))
    }

    /**
     * @throws NoCommonModeError when there is no mode that is supported by both the client and the server.
     */
    fun generateServerHello(): ServerHelloMessage {
        require(step == Step.RECEIVED_PEER_DH_KEY)
        step = Step.SENT_MY_DH_KEY

        val keyPair = keyPairGenerator.generateKeyPair()
        myPrivateDHKey = keyPair.private
        myPublicDHKey = keyPair.public.encoded

        sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
        val commonHeader = CommonHeader(MessageType.SERVER_HELLO, PROTOCOL_VERSION, sessionId, 0, Instant.now().toEpochMilli())

        val commonModes = clientHelloMessage!!.supportedModes.intersect(supportedModes)
        val selectedMode = if (commonModes.isEmpty()) {
            throw NoCommonModeError(clientHelloMessage!!.supportedModes, supportedModes)
        } else {
            commonModes.first()
        }

        serverHelloMessage = ServerHelloMessage(commonHeader, myPublicDHKey!!, selectedMode)
        clientHelloToServerHelloBytes = clientHelloMessage!!.toBytes() + serverHelloMessage!!.toBytes()
        return serverHelloMessage!!
    }

    /**
     * Caution: this is available in cases where one component needs to perform step 2 of the handshake
     * and forward the generated DH key downstream to another component that wil complete the protocol from that point on.
     * This means the private key will be temporarily exposed.
     *
     * That downstream component can resume the protocol from that point onwards creating a new instance of this class using the [fromStep2] method.
     *
     * @return a pair containing (in that order) the private and the public DH key.
     */
    fun getDHKeyPair(): Pair<ByteArray, ByteArray> {
        require(step == Step.SENT_MY_DH_KEY)

        return myPrivateDHKey!!.encoded to myPublicDHKey!!
    }

    fun generateHandshakeSecrets() {
        require(step == Step.SENT_MY_DH_KEY)
        step = Step.GENERATED_HANDSHAKE_SECRETS

        sharedHandshakeSecrets = generateHandshakeSecrets(sharedDHSecret!!, clientHelloToServerHelloBytes!!)
    }

    fun validatePeerHandshakeMessage(clientHandshakeMessage: ClientHandshakeMessage) {
        require(step == Step.GENERATED_HANDSHAKE_SECRETS)
        step = Step.RECEIVED_HANDSHAKE_MESSAGE

        val clientRecordHeaderBytes = clientHandshakeMessage.recordHeader.toBytes()
        clientHandshakePayload = aesCipher.decrypt(clientRecordHeaderBytes, clientHandshakeMessage.tag, sharedHandshakeSecrets!!.initiatorNonce, clientHandshakeMessage.encryptedData, sharedHandshakeSecrets!!.initiatorEncryptionKey)
        val payloadBuffer = ByteBuffer.wrap(clientHandshakePayload)
        val initiatorPublicKeySize = payloadBuffer.int
        val initiatorPublicKeyBytes = ByteArray(initiatorPublicKeySize)
        payloadBuffer.get(initiatorPublicKeyBytes)
        val initiatorPublicKey = stableKeyFactory.generatePublic(X509EncodedKeySpec(initiatorPublicKeyBytes))
        val signatureSize = payloadBuffer.int
        val signatureBytes = ByteArray(signatureSize)
        val clientHelloToClientPublicKey = clientHelloToServerHelloBytes!! + (initiatorPublicKeySize.toByteArray() + initiatorPublicKeyBytes)
        val contentToBeSigned = clientSigPad.toByteArray(Charsets.UTF_8) + hash.digest(clientHelloToClientPublicKey)
        payloadBuffer.get(signatureBytes)
        signature.initVerify(initiatorPublicKey)
        signature.update(contentToBeSigned)
        signature.verify(signatureBytes)
        val macReceivedSize = payloadBuffer.int
        val macReceived = ByteArray(macReceivedSize)
        payloadBuffer.get(macReceived)
        val clientHelloToSignedContent = clientHelloToClientPublicKey + signatureBytes
        val contentToBeMaced = hash.digest(clientHelloToSignedContent)
        hmac.init(sharedHandshakeSecrets!!.initiatorAuthKey)
        hmac.update(contentToBeMaced)
        val initiatorMacCalculated = hmac.doFinal()
        require(initiatorMacCalculated.contentEquals(macReceived)) { "Initiator MAC not valid." }
    }

    /**
     * @param signingFn a callback function that will be invoked for performing signing (with the stable identity key).
     */
    fun generateOurHandshakeMessage(publicKey: PublicKey, signingFn: (ByteArray) -> ByteArray): ServerHandshakeMessage {
        require(step == Step.RECEIVED_HANDSHAKE_MESSAGE)
        step = Step.SENT_HANDSHAKE_MESSAGE

        val serverRecordHeader = CommonHeader(MessageType.SERVER_HANDSHAKE, PROTOCOL_VERSION, sessionId, 1, Instant.now().toEpochMilli())
        val serverRecordHeaderBytes = serverRecordHeader.toBytes()
        val serverPublicKey = publicKey.encoded.size.toByteArray() + publicKey.encoded
        val clientHelloToServerPublicKey = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + serverPublicKey
        val contentToBeSigned = serverSigPad.toByteArray(Charsets.UTF_8) + hash.digest(clientHelloToServerPublicKey)
        val serverSignature = signingFn(contentToBeSigned)
        val clientHelloToServerSignedContent = clientHelloToServerPublicKey + serverSignature
        val contentToBeMaced = hash.digest(clientHelloToServerSignedContent)
        hmac.init(sharedHandshakeSecrets!!.responderAuthKey)
        hmac.update(contentToBeMaced)
        val serverMac = hmac.doFinal()
        serverHandshakePayload = serverPublicKey + (serverSignature.size.toByteArray() + serverSignature) + (serverMac.size.toByteArray() + serverMac)
        val (serverEncryptedData, serverTag) = aesCipher.encryptWithAssociatedData(serverRecordHeaderBytes, sharedHandshakeSecrets!!.responderNonce, serverHandshakePayload!!, sharedHandshakeSecrets!!.responderEncryptionKey)
        return ServerHandshakeMessage(serverRecordHeader, serverEncryptedData, serverTag)
    }

    fun getSession(): AuthenticatedSession {
        require(step == Step.SENT_HANDSHAKE_MESSAGE)
        step = Step.SESSION_ESTABLISHED

        val fullTranscript = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + serverHandshakePayload!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return AuthenticatedSession(sessionId, 2, sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.initiatorEncryptionKey)
    }

}

/**
 * There is no mode that is supported both by the client and the server.
 */
class NoCommonModeError(val clientModes: List<Mode>, val serverModes: List<Mode>): RuntimeException()