package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.FlowMessage
import net.corda.p2p.FlowMessageHeader
import net.corda.p2p.LinkInMessage
import net.corda.p2p.Step2Message
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.LinkManager.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.authenticateAuthenticatedMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkManagerToGatewayMessageFromFlowMessage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

class SessionManagerTest {

    companion object {
        private val GROUP_ID = null
        val PARTY_A = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyA", GROUP_ID)
        val PARTY_B = LinkManagerNetworkMap.NetMapHoldingIdentity("PartyB", GROUP_ID)
        const val MAX_MESSAGE_SIZE = 1024 * 1024
    }

    class MockNetworkMap(nodes: List<LinkManagerNetworkMap.NetMapHoldingIdentity>) {
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val signature = Signature.getInstance("ECDSA", provider)
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)

        private val keys = HashMap<LinkManagerNetworkMap.NetMapHoldingIdentity, KeyPair>()
        private val peerForHash = HashMap<Int, LinkManagerNetworkMap.NetMapHoldingIdentity>()

        private fun MessageDigest.hash(data: ByteArray): ByteArray {
            this.reset()
            this.update(data)
            return digest()
        }

        init {
            for (node in nodes) {
                val keyPair = keyPairGenerator.generateKeyPair()
                keys[node] = keyPair
                peerForHash[messageDigest.hash(keyPair.public.encoded).contentHashCode()] = node
            }
        }

        fun getSessionNetworkMapForNode(node: LinkManagerNetworkMap.NetMapHoldingIdentity): LinkManagerNetworkMap {
            return object : LinkManagerNetworkMap {
                override fun getPublicKey(holdingIdentity: LinkManagerNetworkMap.NetMapHoldingIdentity): PublicKey? {
                    return keys[holdingIdentity]?.public
                }

                override fun getPublicKeyFromHash(hash: ByteArray): PublicKey {
                    val peer = getPeerFromHash(hash)
                    return keys[peer]!!.public
                }

                override fun getPeerFromHash(hash: ByteArray): LinkManagerNetworkMap.NetMapHoldingIdentity? {
                    return peerForHash[hash.contentHashCode()]
                }

                override fun getEndPoint(holdingIdentity: LinkManagerNetworkMap.NetMapHoldingIdentity): LinkManagerNetworkMap.EndPoint? {
                    //This is not needed in this test as it is only used by the Gateway.
                    return LinkManagerNetworkMap.EndPoint("", "")
                }

                override fun getOurPublicKey(groupId: String?): PublicKey? {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return keys[node]!!.public
                }

                override fun getOurHoldingIdentity(groupId: String?): LinkManagerNetworkMap.NetMapHoldingIdentity {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    return node
                }

                override fun signData(groupId: String?, data: ByteArray): ByteArray {
                    assertNull(groupId) {"In this case the groupId should be null."}
                    signature.initSign(keys[node]?.private)
                    signature.update(data)
                    return signature.sign()
                }
            }
        }

    }

    fun mockGatewayResponse(message: InitiatorHelloMessage): Step2Message {
        val authenticationProtocol = AuthenticationProtocolResponder(
            message.header.sessionId,
            listOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE
        )
        authenticationProtocol.receiveInitiatorHello(message)
        val responderHello = authenticationProtocol.generateResponderHello()
        val (privateKey, _) = authenticationProtocol.getDHKeyPair()
        return Step2Message(message, responderHello, ByteBuffer.wrap(privateKey))
    }

    @Test
    fun `A session can be negotiated between two SessionManagers`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val initiatorSessionManager = SessionManager(
            ProtocolMode.AUTHENTICATION_ONLY,
            netMap.getSessionNetworkMapForNode(PARTY_A),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }
        val responderSessionManager = SessionManager(
            ProtocolMode.AUTHENTICATION_ONLY,
            netMap.getSessionNetworkMapForNode(PARTY_B),
            MAX_MESSAGE_SIZE
        ) { _, _, _ -> return@SessionManager }

        val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
        val header = FlowMessageHeader(PARTY_B.toHoldingIdentity(), PARTY_A.toHoldingIdentity(), null, "messageId", "")
        val message = FlowMessage(header, payload)

        val initiatorHelloMessage = initiatorSessionManager.beginSessionNegotiation(getSessionKeyFromMessage(message))
        assertTrue(initiatorHelloMessage.payload is InitiatorHelloMessage)

        //Strip the Header from the message (as the Gateway does before sending it).
        val step2Message = mockGatewayResponse(initiatorHelloMessage.payload as InitiatorHelloMessage)
        assertNull(responderSessionManager.processSessionMessage(LinkInMessage(step2Message)))
        val initiatorHandshakeMessage = initiatorSessionManager.processSessionMessage(LinkInMessage(step2Message.responderHello))

        assertTrue(initiatorHandshakeMessage!!.payload is InitiatorHandshakeMessage)
        val responderHandshakeMessage = responderSessionManager.processSessionMessage(LinkInMessage(initiatorHandshakeMessage.payload))
        assertTrue(responderHandshakeMessage!!.payload is ResponderHandshakeMessage)
        assertNull(initiatorSessionManager.processSessionMessage(LinkInMessage(responderHandshakeMessage.payload)))

        val initiatorSession = initiatorSessionManager.getInitiatorSession(getSessionKeyFromMessage(message))
        assertNotNull(initiatorSession, "Authenticated Session is not stored in the initiator's session manager.")
        val authenticatedMessage = createLinkManagerToGatewayMessageFromFlowMessage(message, initiatorSession!!, netMap.getSessionNetworkMapForNode(PARTY_A))
        assertTrue(authenticatedMessage.payload is AuthenticatedDataMessage)
        val authenticatedDataMessage = (authenticatedMessage.payload as AuthenticatedDataMessage)

        val responderSession = responderSessionManager.getResponderSession(authenticatedDataMessage.header.sessionId)
        assertNotNull(initiatorSession, "Authenticated Session is not stored in the responder's session manager.")
        val responderMessage = authenticateAuthenticatedMessage(authenticatedDataMessage, responderSession!!)
        assertEquals(message.payload, responderMessage!!.payload)
    }
}