import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionManagerNetworkMapTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyB", GROUP_ID)
        val PARTY_NOT_IN_NETMAP = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyImposter", GROUP_ID)
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    @BeforeEach
    fun `Intercept logging`() {

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

        val initiatorHelloMessage = initiatorSessionManager.getSessionInitMessage(sessionKey)
        Assertions.assertNull(initiatorHelloMessage)
    }

}