package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Mode
import net.corda.p2p.crypto.protocol.data.CommonHeader
import net.corda.p2p.crypto.protocol.data.InitiatorHelloMessage
import net.corda.p2p.crypto.protocol.data.MessageType
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class SessionManagerTest {

    companion object {
        const val PARTY_A = "PartyA"
        const val PARTY_B = "PartyB"
        const val GROUP_ID = "MyGroup"
    }

    class MockNetworkMap(nodes: List<String>) {
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val keys = HashMap<String, KeyPair>()
        private val signature = Signature.getInstance("ECDSA", provider)

        init {
            for (node in nodes) {
                keys[node] = keyPairGenerator.generateKeyPair()
            }
        }

        fun getPrivateKey(node: String): PrivateKey? {
            return keys[node]?.private
        }

        fun getPublicKey(node: String): PublicKey? {
            return keys[node]?.public
        }

        fun signData(node: String, data: ByteArray): ByteArray {
            signature.initSign(getPrivateKey(node))
            signature.update(data)
            return signature.sign()
        }
    }

    fun mockGatewayResponse(message: InitiatorSessionMessage.InitiatorHello, us: Peer): InitiatorSessionMessage.Step2Message {
        val authenticationProtocol = AuthenticationProtocolResponder(message.header.sessionId, listOf(Mode.AUTHENTICATION_ONLY))
        authenticationProtocol.receiveInitiatorHello(message.message)
        val responderHello = authenticationProtocol.generateResponderHello()
        val (privateKey, publicKey) = authenticationProtocol.getDHKeyPair()
        return InitiatorSessionMessage.Step2Message(Header(message.header.source, us, message.header.sessionId), message.message, responderHello, privateKey, publicKey)
    }

    @Test
    fun `A session can be negotiated between SessionManagerInitiator and SessionManagerResponder`() {
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val initiatorSessionManager = SessionManagerInitiator(
            Mode.AUTHENTICATION_ONLY,
            PARTY_A,
            netMap::getPublicKey,
            {data -> netMap.signData(PARTY_A, data)},
            GROUP_ID
        )
        val responderSessionManager = SessionManagerResponder(
            Mode.AUTHENTICATION_ONLY,
            PARTY_B,
            netMap::getPublicKey
        ) { data -> netMap.signData(PARTY_B, data) }

        val payload = "Hello from PartyA".toByteArray()
        val message = SessionMessage(payload, PARTY_B)
        initiatorSessionManager.sendMessage(message)
        val initiatorHelloMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(initiatorHelloMessage is InitiatorSessionMessage.InitiatorHello)

        val step2Message = mockGatewayResponse(initiatorHelloMessage as InitiatorSessionMessage.InitiatorHello, PARTY_B)
        responderSessionManager.processSessionMessage(step2Message)
        initiatorSessionManager.processSessionMessage(ResponderSessionMessage.ResponderHello(Header(PARTY_B, PARTY_A, step2Message.header.sessionId), step2Message.responderHelloMsg))

        val initiatorHandshakeMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(initiatorHandshakeMessage is InitiatorSessionMessage.InitiatorHandshake)
        responderSessionManager.processSessionMessage(initiatorHandshakeMessage as InitiatorSessionMessage)

        val responderHandshakeMessage = responderSessionManager.getQueuedOutboundMessage()
        assertTrue(responderHandshakeMessage is ResponderSessionMessage.ResponderHandshake)
        initiatorSessionManager.processSessionMessage(responderHandshakeMessage as ResponderSessionMessage)
        val authenticatedMessage = initiatorSessionManager.getQueuedOutboundMessage()
        assertTrue(authenticatedMessage is AuthenticatedMessage)
        assertEquals((authenticatedMessage as AuthenticatedMessage).payload, payload)

        responderSessionManager.processAuthenticatedMessage(authenticatedMessage)
        val queuedMessage = responderSessionManager.getQueuedInboundMessage()
        assertEquals(queuedMessage?.payload, payload)
    }

    @Test
    fun `SessionManagerResponder correctly handles InitiatorHello message`() {
        val commonHeader = CommonHeader(MessageType.INITIATOR_HELLO, 1, "Id", 0, 100)
        val message = InitiatorHelloMessage(commonHeader, "PublicKey".toByteArray(), listOf(Mode.AUTHENTICATION_ONLY))
        val outerMessage = InitiatorSessionMessage.InitiatorHello(Header("Peer", "Dest", "Id"), message)
        val netMap = MockNetworkMap(listOf(PARTY_A, PARTY_B))
        val responderSessionManager = SessionManagerResponder(
            Mode.AUTHENTICATION_ONLY,
            PARTY_B,
            netMap::getPublicKey
        ) { data -> netMap.signData(PARTY_B, data) }
        assertThrows<IllegalArgumentException> { responderSessionManager.processSessionMessage(outerMessage) }
    }
}