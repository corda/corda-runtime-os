package net.corda.p2p.crypto

import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

class AuthenticationProtocolInitiator(private val sessionId: String): AuthenticationProtocol() {

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

        clientHelloMessage = ClientHelloMessage(sessionId, Instant.now().toEpochMilli(), myPublicDHKey!!)
        return clientHelloMessage!!
    }

    fun receiveServerHello(serverHelloMsg: ServerHelloMessage) {
        require(step == Step.SENT_MY_DH_KEY)
        step = Step.RECEIVED_PEER_DH_KEY

        serverHelloMessage = serverHelloMsg
        clientHelloToServerHelloBytes = objectMapper.writeValueAsBytes(clientHelloMessage) + objectMapper.writeValueAsBytes(serverHelloMessage)
        peerPublicDHKey = ephemeralKeyFactory.generatePublic(X509EncodedKeySpec(serverHelloMsg.publicKey))
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
    fun generateOurHandshakeMessage(publicKey: PublicKey, signingFn: (ByteArray) -> ByteArray): ClientHandshakeMessage {
        require(step == Step.GENERATED_HANDSHAKE_SECRETS)
        step = Step.SENT_HANDSHAKE_MESSAGE

        val clientRecordHeader = RecordHeader(sessionId, Instant.now().toEpochMilli())
        val clientRecordHeaderBytes = objectMapper.writeValueAsBytes(clientRecordHeader)
        val clientPublicKey = publicKey.encoded.size.toByteArray() + publicKey.encoded
        val clientHelloToClientPublicKey = clientHelloToServerHelloBytes!! + clientPublicKey
        val contentToBeSignedByClient = clientSigPad.toByteArray(Charsets.UTF_8) + hash.digest(clientHelloToClientPublicKey)
        val clientSignature = signingFn(contentToBeSignedByClient)
        val clientHelloToClientSignedContent = clientHelloToClientPublicKey + clientSignature
        val contentToBeMacedByClient = hash.digest(clientHelloToClientSignedContent)
        hmac.init(sharedHandshakeSecrets!!.initiatorAuthKey)
        hmac.update(contentToBeMacedByClient)
        val clientMac = hmac.doFinal()
        clientHandshakePayload = clientPublicKey + (clientSignature.size.toByteArray() + clientSignature) + (clientMac.size.toByteArray() + clientMac)

        val nonce = sharedHandshakeSecrets!!.initiatorNonce
        val (clientEncryptedData, clientTag) = aesCipher.encryptWithAssociatedData(clientRecordHeaderBytes, nonce, clientHandshakePayload!!, sharedHandshakeSecrets!!.initiatorEncryptionKey)
        return ClientHandshakeMessage(clientRecordHeader, clientEncryptedData, clientTag)
    }


    fun validatePeerHandshakeMessage(serverHandshakeMessage: ServerHandshakeMessage) {
        require(step == Step.SENT_HANDSHAKE_MESSAGE)
        step = Step.RECEIVED_HANDSHAKE_MESSAGE

        val serverRecordHeader = objectMapper.writeValueAsBytes(serverHandshakeMessage.recordHeader)
        serverHandshakePayload = aesCipher.decrypt(serverRecordHeader, serverHandshakeMessage.tag, sharedHandshakeSecrets!!.responderNonce, serverHandshakeMessage.encryptedData, sharedHandshakeSecrets!!.responderEncryptionKey)
        val responderHandshakeMessagePayloadDecryptedBuffer = ByteBuffer.wrap(serverHandshakePayload)
        val responderPublicKeySize = responderHandshakeMessagePayloadDecryptedBuffer.int
        val responderPublicKeyBytes = ByteArray(responderPublicKeySize)
        responderHandshakeMessagePayloadDecryptedBuffer.get(responderPublicKeyBytes)
        val responderPublicKey = stableKeyFactory.generatePublic(X509EncodedKeySpec(responderPublicKeyBytes))
        val responderSignatureSize = responderHandshakeMessagePayloadDecryptedBuffer.int
        val responderSignatureBytes = ByteArray(responderSignatureSize)
        val clientHelloToServerPublicKey = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + (responderPublicKeySize.toByteArray() + responderPublicKeyBytes)
        val contentToBeSigned = serverSigPad.toByteArray(Charsets.UTF_8) + hash.digest(clientHelloToServerPublicKey)
        responderHandshakeMessagePayloadDecryptedBuffer.get(responderSignatureBytes)
        signature.initVerify(responderPublicKey)
        signature.update(contentToBeSigned)
        signature.verify(responderSignatureBytes)
        val receivedMacSize = responderHandshakeMessagePayloadDecryptedBuffer.int
        val receivedMac = ByteArray(receivedMacSize)
        responderHandshakeMessagePayloadDecryptedBuffer.get(receivedMac)
        val clientHelloToServerSignedContent = clientHelloToServerPublicKey + responderSignatureBytes
        val contentToBeMaced = hash.digest(clientHelloToServerSignedContent)
        hmac.init(sharedHandshakeSecrets!!.responderAuthKey)
        hmac.update(contentToBeMaced)
        val calculatedMac = hmac.doFinal()
        require(calculatedMac.contentEquals(receivedMac)) { "Responder MAC not valid." }
    }

    fun getSession(): AuthenticatedSession {
        require(step == Step.RECEIVED_HANDSHAKE_MESSAGE)
        step = Step.SESSION_ESTABLISHED

        val fullTranscript = clientHelloToServerHelloBytes!! + clientHandshakePayload!! + serverHandshakePayload!!
        val sharedSessionSecrets = generateSessionSecrets(sharedDHSecret!!, fullTranscript)
        return AuthenticatedSession(sharedSessionSecrets.initiatorEncryptionKey, sharedSessionSecrets.initiatorNonce, sharedSessionSecrets.responderEncryptionKey, sharedSessionSecrets.responderNonce)
    }

}