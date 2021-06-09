package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.FlowMessageHeader
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.InitiatorHelloMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.Step2Message
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.sessions.SessionNetworkMap.Companion.toAvroPeer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import kotlin.collections.HashMap

class SessionManagerTest {

    companion object {
        const val GROUP_ID = "MyGroup"
        val PARTY_A = SessionNetworkMap.Peer("PartyA", GROUP_ID)
        val PARTY_B = SessionNetworkMap.Peer("PartyB", GROUP_ID)
    }

    class MockNetworkMap(nodes: List<SessionNetworkMap.Peer>) {
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val signature = Signature.getInstance("ECDSA", provider)
        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, provider)

        private val keys = HashMap<SessionNetworkMap.Peer, KeyPair>()
        private val peerForHash = HashMap<Int, SessionNetworkMap.Peer>()

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

        fun getSessionNetworkMapForNode(node: SessionNetworkMap.Peer): SessionNetworkMap {
            return object : SessionNetworkMap {
                override fun getPublicKey(peer: SessionNetworkMap.Peer): PublicKey? {
                    return keys[peer]?.public
                }

                override fun getPublicKeyFromHash(hash: ByteArray): PublicKey {
                    val peer = getPeerFromHash(hash)
                    return keys[peer]!!.public
                }

                override fun getPeerFromHash(hash: ByteArray): SessionNetworkMap.Peer? {
                    return peerForHash[hash.contentHashCode()]
                }

                override fun getEndPoint(peer: SessionNetworkMap.Peer): SessionNetworkMap.EndPoint? {
                    //This is not needed in this test as it is only used by the Gateway.
                    return SessionNetworkMap.EndPoint("", "")
                }

                override fun getOurPublicKey(): PublicKey {
                    return keys[node]!!.public
                }

                override fun getOurPeer(): SessionNetworkMap.Peer {
                    return node
                }

                override fun signData(data: ByteArray): ByteArray {
                    signature.initSign(keys[node]?.private)
                    signature.update(data)
                    return signature.sign()
                }
            }
        }

    }

    fun mockGatewayResponse(message: InitiatorHelloMessage): Step2Message {
        val authenticationProtocol = AuthenticationProtocolResponder(message.header.sessionId, listOf(ProtocolMode.AUTHENTICATION_ONLY))
        authenticationProtocol.receiveInitiatorHello(message)
        val responderHello = authenticationProtocol.generateResponderHello()
        val (privateKey, publicKey) = authenticationProtocol.getDHKeyPair()
        return Step2Message(message, responderHello, ByteBuffer.wrap(privateKey), ByteBuffer.wrap(publicKey))
    }

    @Test
    fun `A session can be negotiated between SessionManagerInitiator and SessionManagerResponder`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val initiatorSessionManager = SessionManagerInitiator(
            ProtocolMode.AUTHENTICATION_ONLY,
            netMap.getSessionNetworkMapForNode(PARTY_A)
        )
        val responderSessionManager = SessionManagerResponder(
            ProtocolMode.AUTHENTICATION_ONLY,
            netMap.getSessionNetworkMapForNode(PARTY_B)
        )

        val payload = ByteBuffer.wrap("Hello from PartyA".toByteArray())
        val header = FlowMessageHeader(PARTY_B.toAvroPeer(), PARTY_A.toAvroPeer(), null, "messageId", "")
        val message = FlowMessage(header, payload)
        initiatorSessionManager.sendMessage(message)
        val initiatorHelloMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(initiatorHelloMessage.payload is InitiatorHelloMessage)

        //Strip the Header from the message (as the Gateway does before sending it).
        val step2Message = mockGatewayResponse(initiatorHelloMessage.payload as InitiatorHelloMessage)
        responderSessionManager.processMessage(step2Message)
        initiatorSessionManager.processSessionMessage(step2Message.responderHello)

        val initiatorHandshakeMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(initiatorHandshakeMessage.payload is InitiatorHandshakeMessage)
        responderSessionManager.processMessage(initiatorHandshakeMessage.payload)

        val responderHandshakeMessage = responderSessionManager.getQueuedOutboundMessage()
        assertTrue(responderHandshakeMessage!!.payload is ResponderHandshakeMessage)
        initiatorSessionManager.processSessionMessage(responderHandshakeMessage.payload)
        val authenticatedMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(authenticatedMessage.payload is AuthenticatedDataMessage)

        responderSessionManager.processMessage(authenticatedMessage.payload)
        val queuedMessage = responderSessionManager.getQueuedInboundMessage()
        assertEquals(queuedMessage?.payload, payload)
    }
}