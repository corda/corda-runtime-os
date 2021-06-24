package net.corda.p2p.linkmanager.sessions

import com.nhaarman.mockito_kotlin.*
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging
import net.corda.p2p.linkmanager.sessions.SessionManagerTest.Companion.sessionManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.util.*

class SessionManagerNetworkMapTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.HoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.HoldingIdentity("PartyB", GROUP_ID)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.HoldingIdentity("PartyImposter", GROUP_ID)
        val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint("10.0.0.1:hello")
    }

    private fun negotiateToResponderHelloMessage(netMap: LinkManagerNetworkMap, mockLogger: Logger): Pair<String, LinkOutMessage?> {
        val sessionManager = sessionManager(netMap)
        val (step2Message, responderHello) = negotiateToResponderHelloMessage(sessionManager, mockLogger)
        return Pair(step2Message.initiatorHello.header.sessionId, responderHello)
    }

    private fun negotiateToResponderHelloMessage(
        sessionManager: SessionManagerImpl,
        mockLogger: Logger,
        supportedMode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY,
    ): Pair<Step2Message, LinkOutMessage?> {

        val sessionKey = SessionManagerImpl.SessionKey(null, PARTY_B)
        sessionManager.setLogger(mockLogger)
        Messaging.setLogger(mockLogger)

        val initiatorHelloMessage = sessionManager.getSessionInitMessage(sessionKey)
        val step2Message = SessionManagerTest().mockGatewayResponse(
            initiatorHelloMessage?.payload as InitiatorHelloMessage,
            supportedMode
        )
        return Pair(step2Message, sessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))
    }

    private fun negotiateToInitiatorHandshakeMessage(
        initiatorNetMap: LinkManagerNetworkMap,
        responderNetMap: LinkManagerNetworkMap,
        mockLogger: Logger
    ): Pair<String, LinkOutMessage?> {
        val initiatorSessionManager = sessionManager(initiatorNetMap)
        val responderSessionManager = sessionManager(responderNetMap)

        responderSessionManager.setLogger(mockLogger)
        initiatorSessionManager.setLogger(mockLogger)

        return negotiateToInitiatorHandshakeMessage(initiatorSessionManager, responderSessionManager, mockLogger)
    }

    private fun negotiateToInitiatorHandshakeMessage(
        initiatorSessionManager: SessionManagerImpl,
        responderSessionManager: SessionManagerImpl,
        mockLogger: Logger
    ): Pair<String, LinkOutMessage?> {

        responderSessionManager.setLogger(mockLogger)
        val (step2Message, initiatorHandshake) = negotiateToResponderHelloMessage(initiatorSessionManager, mockLogger)
        responderSessionManager.processSessionMessage(LinkInMessage(step2Message))

        return Pair(
            step2Message.initiatorHello.header.sessionId,
            responderSessionManager.processSessionMessage(LinkInMessage(initiatorHandshake!!.payload))
        )
    }

    private fun negotiateToResponderHandshakeMessage(
        initiatorNetMap: LinkManagerNetworkMap,
        responderNetMap: LinkManagerNetworkMap,
        mockLogger: Logger
    ): String {
        val initiatorSessionManager = sessionManager(initiatorNetMap)
        val responderSessionManager = sessionManager(responderNetMap)

        val (sessionId, responderHandshakeMessage) = negotiateToInitiatorHandshakeMessage(
            initiatorSessionManager,
            responderSessionManager,
            mockLogger
        )
        initiatorSessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage!!.payload))
        return sessionId
    }

    fun makeMockNetworkMap(): LinkManagerNetworkMap {
        val keyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(PARTY_B)).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(keyPair.public)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(keyPair.public)
        Mockito.`when`(netMap.getOurPrivateKey(anyOrNull())).thenReturn(keyPair.private)
        return netMap
    }

    private fun hashKey(key: PublicKey): ByteArray {
        val provider = BouncyCastleProvider()
        val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)
        messageDigest.reset()
        messageDigest.update(key.encoded)
        return messageDigest.digest()
    }

    private fun hashKeyToBase64(key: PublicKey): String {
        return Base64.getEncoder().encodeToString(hashKey(key))
    }

    @Test
    fun `Cannot generate a session init message for a party not in the network map`() {
        val netMap = SessionManagerTest.MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode = setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val initiatorSessionManager = SessionManagerImpl(
            supportedMode,
            netMap.getSessionNetworkMapForNode(SessionManagerTest.PARTY_A),
            SessionManagerTest.MockCryptoService(),
            SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManagerImpl }

        val sessionKey = SessionManagerImpl.SessionKey(null, PARTY_NOT_IN_NETMAP)

        val mockLogger = Mockito.mock(Logger::class.java)
        initiatorSessionManager.setLogger(mockLogger)
        Messaging.setLogger(mockLogger)

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(sessionKey)
        Assertions.assertNull(initiatorHelloMessage)
        Mockito.verify(mockLogger).warn(
            "Attempted to send message to peer ${PARTY_NOT_IN_NETMAP.toHoldingIdentity()} which is" +
                    " not in the network map. The message was discarded."
        )
    }

    @Test
    fun `Responder hello message is dropped if we are not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(any())).thenReturn(null)
        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToResponderHelloMessage(netMap, mockLogger)

        Assertions.assertNull(response)
        Mockito.verify(mockLogger)
            .warn("Received ${ResponderHelloMessage::class.java.simpleName} with sessionId $sessionId" +
                    " but cannot find public key for our group identity null. The message was discarded.")
    }

    @Test
    fun `Responder hello message is dropped if the receiver is not in the network map`() {
        val publicKey = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair().public
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(publicKey)

        val mockLogger = Mockito.mock(Logger::class.java)
        val (sessionId, response) = negotiateToResponderHelloMessage(netMap, mockLogger)

        Assertions.assertNull(response)
        Mockito.verify(mockLogger).warn(
            "Received ResponderHelloMessage with sessionId ${sessionId}. From peer $PARTY_B which is not" +
            " in the network map. The message was discarded."
        )
    }

    @Test
    fun `Responder hello message is dropped if we are removed from the network map during processSessionMessage`() {
        val publicKey = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair().public
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(FAKE_ENDPOINT)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(publicKey)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(publicKey)
        Mockito.`when`(netMap.getOurPrivateKey(anyOrNull())).thenReturn(null)

        val mockLogger = Mockito.mock(Logger::class.java)
        val (_, response) = negotiateToResponderHelloMessage(netMap, mockLogger)

        Assertions.assertNull(response)
        Mockito.verify(mockLogger).warn("Could not find (our) private key in the network map for group = null." +
                " The ${ResponderHelloMessage::class.java.simpleName} was discarded.")
    }

    @Test
    fun `Responder hello message is dropped if the receiver is removed from the network map during processSessionMessage`() {
        val keyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(PARTY_B)).thenReturn(FAKE_ENDPOINT).thenReturn(null)
        Mockito.`when`(netMap.getOurPublicKey(anyOrNull())).thenReturn(keyPair.public)
        Mockito.`when`(netMap.getPublicKey(anyOrNull())).thenReturn(keyPair.public)
        Mockito.`when`(netMap.getOurPrivateKey(anyOrNull())).thenReturn(keyPair.private)

        val mockLogger = Mockito.mock(Logger::class.java)
        val (_, response) = negotiateToResponderHelloMessage(netMap, mockLogger)

        Assertions.assertNull(response)
        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${PARTY_B.toHoldingIdentity()} which is not in the network map." +
                " The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender public key hash is not in the network map`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        val keyHash = hashKeyToBase64(initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))!!)
        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Could not find the public key in the network map by hash = $keyHash. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver public key hash is not in the network map`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val initiatorPublicKey = initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(anyOrNull())).thenReturn(initiatorPublicKey)
        Mockito.`when`(responderNetMap.getPeerFromHash(anyOrNull())).thenReturn(null)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        val keyHash = hashKeyToBase64(initiatorPublicKey!!)
        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " The received public key hash ($keyHash) corresponding to one of our holding identities is not in the network map." +
                " The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the receiver is removed from the network map`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val initiatorPublicKey = initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(anyOrNull())).thenReturn(initiatorPublicKey)
        Mockito.`when`(responderNetMap.getPeerFromHash(anyOrNull())).thenReturn(PARTY_B)
        Mockito.`when`(responderNetMap.getOurPublicKey(anyOrNull())).thenReturn(null)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId" +
                " but cannot find public key for our group identity null. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val initiatorPublicKey = initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))
        val initiatorKeyHash = hashKey(initiatorPublicKey!!)
        val responderPublicKey = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair().public

        Mockito.`when`(responderNetMap.getPublicKeyFromHash(initiatorKeyHash)).thenReturn(initiatorPublicKey)
        Mockito.`when`(responderNetMap.getPeerFromHash(anyOrNull())).thenReturn(PARTY_B)
        Mockito.`when`(responderNetMap.getOurPublicKey(anyOrNull())).thenReturn(responderPublicKey)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " Could not find (our) private key in the network map for group = null. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map before the second last step`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val initiatorPublicKey = initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))
        val initiatorKeyHash = hashKey(initiatorPublicKey!!)
        val responderKeyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val responderKeyHash = hashKey(responderKeyPair!!.public)

        Mockito.`when`(responderNetMap.getPublicKeyFromHash(initiatorKeyHash)).thenReturn(initiatorPublicKey)
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(responderKeyHash)).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getPeerFromHash(initiatorKeyHash)).thenReturn(PARTY_B).thenReturn(null)
        Mockito.`when`(responderNetMap.getOurPublicKey(anyOrNull())).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getOurPrivateKey(anyOrNull())).thenReturn(responderKeyPair.private)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (sessionId, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        Mockito.verify(mockLogger).warn("Received ${InitiatorHandshakeMessage::class.java.simpleName} with sessionId $sessionId." +
                " The received public key hash (${hashKeyToBase64(initiatorPublicKey)}) corresponding to one of the senders holding" +
                " identities is not in the network map. The message was discarded.")
    }

    @Test
    fun `Initiator handshake message is dropped if the sender is removed from the network map before the last step`() {
        val initiatorNetMap = makeMockNetworkMap()
        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val initiatorPublicKey = initiatorNetMap.getPublicKey(LinkManagerNetworkMap.HoldingIdentity("", ""))
        val initiatorKeyHash = hashKey(initiatorPublicKey!!)
        val responderKeyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val responderKeyHash = hashKey(responderKeyPair.public!!)

        Mockito.`when`(responderNetMap.getPublicKeyFromHash(initiatorKeyHash)).thenReturn(initiatorPublicKey)
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(responderKeyHash)).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getPeerFromHash(anyOrNull())).thenReturn(PARTY_B)
        Mockito.`when`(responderNetMap.getOurPublicKey(anyOrNull())).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getOurPrivateKey(anyOrNull())).thenReturn(responderKeyPair.private)

        val mockLogger = Mockito.mock(Logger::class.java)

        val (_, response) = negotiateToInitiatorHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Assertions.assertNull(response)

        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${PARTY_B.toHoldingIdentity()} which is not in the network map." +
                " The message was discarded.")
    }


    @Test
    fun `Responder handshake message is dropped if the sender is not in the network map`() {
        val initiatorKeyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val initiatorNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(initiatorNetMap.getEndPoint(PARTY_B)).thenReturn(LinkManagerNetworkMap.EndPoint(""))
        Mockito.`when`(initiatorNetMap.getOurPublicKey(anyOrNull())).thenReturn(initiatorKeyPair.public)

        //Called for the first time in `processResponderHello` and the second time in processResponderHandshake.
        Mockito.`when`(initiatorNetMap.getPublicKey(anyOrNull())).thenReturn(initiatorKeyPair.public).thenReturn(null)
        Mockito.`when`(initiatorNetMap.getOurPrivateKey(anyOrNull())).thenReturn(initiatorKeyPair.private)

        val initiatorKeyHash = hashKey(initiatorKeyPair.public)
        val responderKeyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).generateKeyPair()
        val responderKeyHash = hashKey(responderKeyPair.public)

        val responderNetMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(initiatorKeyHash)).thenReturn(initiatorKeyPair.public)
        Mockito.`when`(responderNetMap.getPublicKeyFromHash(responderKeyHash)).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getPeerFromHash(anyOrNull())).thenReturn(PARTY_B)
        Mockito.`when`(responderNetMap.getOurPublicKey(anyOrNull())).thenReturn(responderKeyPair.public)
        Mockito.`when`(responderNetMap.getOurPrivateKey(anyOrNull())).thenReturn(responderKeyPair.private)
        Mockito.`when`(responderNetMap.getEndPoint(anyOrNull())).thenReturn(LinkManagerNetworkMap.EndPoint(""))

        val mockLogger = Mockito.mock(Logger::class.java)

        val sessionId = negotiateToResponderHandshakeMessage(initiatorNetMap, responderNetMap, mockLogger)
        Mockito.verify(mockLogger).warn("Received ${ResponderHandshakeMessage::class.java.simpleName} with sessionId $sessionId. From peer " +
                "$PARTY_B which is not in the network map. The message was discarded.")
    }

}