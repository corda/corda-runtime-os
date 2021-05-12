package net.corda.p2p.crypto

import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

class AuthenticationProtocolResponder(private val sessionId: String): AuthenticationProtocol() {

    companion object {
        fun fromStep2(sessionId: String, clientHelloMsg: ClientHelloMessage, serverHelloMsg: ServerHelloMessage, privateDHKey: ByteArray, publicDHKey: ByteArray): AuthenticationProtocolResponder {
            val protocol = AuthenticationProtocolResponder(sessionId)
            protocol.apply {
                receiveClientHello(clientHelloMsg)
                myPrivateDHKey = protocol.ephemeralKeyFactory.generatePrivate(PKCS8EncodedKeySpec(privateDHKey))
                myPublicDHKey = publicDHKey

                sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
                serverHelloMessage = serverHelloMsg
                clientHelloToServerHelloBytes = objectMapper.writeValueAsBytes(clientHelloMessage) + objectMapper.writeValueAsBytes(serverHelloMessage)

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
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(clientHelloMsg.publicKey))
    }

    fun generateServerHello(): ServerHelloMessage {
        require(step == Step.RECEIVED_PEER_DH_KEY)
        step = Step.SENT_MY_DH_KEY

        val keyPair = keyPairGenerator.generateKeyPair()
        myPrivateDHKey = keyPair.private
        myPublicDHKey = keyPair.public.encoded

        sharedDHSecret = keyAgreement.perform(myPrivateDHKey!!, peerPublicDHKey!!)
        serverHelloMessage = ServerHelloMessage(sessionId, Instant.now().toEpochMilli(), myPublicDHKey!!)
        clientHelloToServerHelloBytes = objectMapper.writeValueAsBytes(clientHelloMessage) + objectMapper.writeValueAsBytes(serverHelloMessage)
        return serverHelloMessage!!
    }

    /**
     * Caution: this is available in cases where one component needs to perform step 2 of the handshake
     * and forward the generated DH key downstream to another component that wil complete the protocol from that point on.
     * This means the private key will be temporarily exposed, which is not ideal.
     *
     * That downstream component can resume the protocol from that point onwards using the [fromStep2] method.
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

        val clientRecordHeaderBytes = objectMapper.writeValueAsBytes(clientHandshakeMessage.recordHeader)
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

        val serverRecordHeader = RecordHeader(sessionId, Instant.now().toEpochMilli())
        val serverRecordHeaderBytes = objectMapper.writeValueAsBytes(serverRecordHeader)
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
        return AuthenticatedSession(sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.responderNonce, sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.initiatorNonce)
    }

}