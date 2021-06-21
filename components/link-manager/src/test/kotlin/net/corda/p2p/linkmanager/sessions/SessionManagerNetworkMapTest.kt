import com.nhaarman.mockito_kotlin.*
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerTest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import java.security.KeyPairGenerator

class SessionManagerNetworkMapTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.HoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.HoldingIdentity("PartyB", GROUP_ID)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.HoldingIdentity("PartyImposter", GROUP_ID)
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    @Test
    fun `Cannot generate a session init message for a party not in the network map`() {
        val netMap = SessionManagerTest.MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode = setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(SessionManagerTest.PARTY_A),
            SessionManagerTest.MockCryptoService(),
            SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val sessionKey = SessionManager.SessionKey(null, PARTY_NOT_IN_NETMAP)

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

    private fun negotiateToResponderHelloMessage(netMap: LinkManagerNetworkMap, mockLogger: Logger): Pair<String, LinkOutMessage?> {
        val supportedMode = setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val sessionManager = SessionManager(
            supportedMode,
            netMap,
            SessionManagerTest.MockCryptoService(),
            SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }
        val (step2Message, responderHello) = negotiateToResponderHelloMessage(sessionManager, supportedMode, mockLogger)
        return Pair(step2Message.initiatorHello.header.sessionId, responderHello)
    }

    private fun negotiateToResponderHelloMessage(
        sessionManager: SessionManager,
        supportedModes: Set<ProtocolMode>,
        mockLogger: Logger): Pair<Step2Message, LinkOutMessage?> {

        val sessionKey = SessionManager.SessionKey(null, PARTY_B)
        sessionManager.setLogger(mockLogger)
        Messaging.setLogger(mockLogger)

        val initiatorHelloMessage = sessionManager.getSessionInitMessage(sessionKey)
        val step2Message = SessionManagerTest().mockGatewayResponse(
            initiatorHelloMessage?.payload as InitiatorHelloMessage,
            supportedModes
        )
        return Pair(step2Message, sessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello)))
    }

    private fun negotiateToInitiatorHelloMessage(netMap: LinkManagerNetworkMap, mockLogger: Logger): Pair<String, LinkOutMessage?> {
        val supportedMode = setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMap,
            SessionManagerTest.MockCryptoService(),
            SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }
        val (step2Message, _) = negotiateToResponderHelloMessage(initiatorSessionManager, supportedMode, mockLogger)

        val responderSessionManager = SessionManager(
        supportedMode,
        netMap,
        SessionManagerTest.MockCryptoService(),
        SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        return Pair(
            step2Message.initiatorHello.header.sessionId,
            responderSessionManager.processSessionMessage(LinkInMessage(step2Message.initiatorHello))
        )
    }

    @Test
    fun `Responder hello message is dropped if we are not in the network map`() {
        val netMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("", ""))
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
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("", ""))
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
        Mockito.`when`(netMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("", ""))
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
        Mockito.`when`(netMap.getEndPoint(PARTY_B)).thenReturn(LinkManagerNetworkMap.EndPoint("", "")).thenReturn(null)
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
    fun `Responder handshake message is dropped if the sender is not in the network map`() {
        TODO()
    }
}