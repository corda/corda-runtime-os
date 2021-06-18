import net.corda.p2p.LinkInMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SessionManagerNetworkMapTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyB", GROUP_ID)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyImposter", GROUP_ID)
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    @Test
    fun `Cannot generate a session init message for a party not in the network map`() {
        val netMap = SessionManagerTest.MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val supportedMode =  setOf(ProtocolMode.AUTHENTICATION_ONLY)
        val initiatorSessionManager = SessionManager(
            supportedMode,
            netMap.getSessionNetworkMapForNode(SessionManagerTest.PARTY_A),
            SessionManagerTest.MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val sessionKey = SessionManager.SessionKey(null, PARTY_NOT_IN_NETMAP)

        val mockLogger = Mockito.mock(Logger::class.java)
        initiatorSessionManager.setLogger(mockLogger)
        Messaging.setLogger(mockLogger)

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(sessionKey)
        Assertions.assertNull(initiatorHelloMessage)
        Mockito.verify(mockLogger).warn("Attempted to send message to peer ${PARTY_NOT_IN_NETMAP.toHoldingIdentity()} which is" +
                " not in the network map. The message was discarded.")
    }

    @Test
    fun `Responder hello message is dropped we are not in the network map`() {

    }

    @Test
    fun `Responder hello message is dropped if the is not in the network map`() {

    }

}