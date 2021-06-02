package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import java.util.concurrent.ConcurrentHashMap
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.Mode
import net.corda.p2p.crypto.protocol.data.CommonHeader
import net.corda.p2p.crypto.protocol.data.InitiatorHandshakeMessage
import net.corda.p2p.crypto.protocol.data.InitiatorHelloMessage
import net.corda.p2p.crypto.protocol.data.MessageType
import net.corda.p2p.crypto.protocol.data.ResponderHandshakeMessage
import net.corda.p2p.crypto.protocol.data.ResponderHelloMessage
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.slf4j.LoggerFactory

class SessionManagerInitiator(
    private val mode: Mode,
    private val us: Peer,
    private val networkMap: (Peer) -> PublicKey?,
    private val signingFn: (ByteArray) -> ByteArray,
    private val groupId: String
    ) {

    private val pendingSessions = ConcurrentHashMap<Peer, AuthenticationProtocolInitiator>()
    private val activeSessions = ConcurrentHashMap<Peer, AuthenticatedSession>()
    private val queuedMessages = ConcurrentLinkedQueue<Any>()
    private val queuedMessagesPendingSession = ConcurrentHashMap<Peer, ConcurrentLinkedQueue<SessionMessage>>()
    private val logger = LoggerFactory.getLogger(this::class.java.name)

    fun sendMessage(message: SessionMessage) {
        val session = activeSessions[message.dest]
        if (session != null) {
            val mac = session.createMac(message.payload)
            queuedMessages.add(AuthenticatedMessage(us, message.dest, message.payload, mac))
        } else {
            queuedMessagesPendingSession
                .computeIfAbsent(message.dest) { ConcurrentLinkedQueue<SessionMessage>() }.add(message)
            if (pendingSessions[message.dest] == null) {
                beginSessionNegotiation(message.dest)
            }
        }
    }

    fun processSessionMessage(message: ResponderSessionMessage) {
        val session = pendingSessions[message.header.source]
        if (session == null) {
            logger.warn("Received ${message::class.java::getSimpleName} from peer ${message.header.source} " +
                "but there is no pending session. The message was discarded.")
            return
        }
        when(message) {
            is ResponderSessionMessage.ResponderHello -> processResponderHello(message, session)
            is ResponderSessionMessage.ResponderHandshake -> processResponderHandshake(message, session)
        }
    }

    fun getQueuedOutboundMessage(): Any? {
        return queuedMessages.poll()
    }

    private fun beginSessionNegotiation(peer: String) {
        val id = UUID.randomUUID().toString()
        val session = AuthenticationProtocolInitiator(id, listOf(mode))
        pendingSessions[peer] = session
        queuedMessages.add(InitiatorSessionMessage.InitiatorHello(Header(us, peer, id), session.generateInitiatorHello()))
    }

    private fun processResponderHello(
        message: ResponderSessionMessage.ResponderHello,
        session: AuthenticationProtocolInitiator
    ) {
        session.receiveResponderHello(message.message)
        session.generateHandshakeSecrets()
        val ourKey = networkMap(us)
        if (ourKey == null) {
            logger.warn("Could not find our identity ($us) in the network map.")
            return
        }
        val responderKey = networkMap(message.header.source)
        if (responderKey == null) {
            logger.info("Received ${ResponderSessionMessage.ResponderHello::class.java.simpleName} from peer " +
                "(${message.header.source}) which is not in the network map.")
            return
        }
        val outboundMessage = InitiatorSessionMessage.InitiatorHandshake(
            Header(us, message.header.source, message.header.sessionId),
            session.generateOurHandshakeMessage(ourKey, responderKey, groupId, signingFn)
        )
        queuedMessages.add(outboundMessage)
    }

    private fun processResponderHandshake(
        message: ResponderSessionMessage.ResponderHandshake,
        session: AuthenticationProtocolInitiator
    ) {
        val responderKey = networkMap(message.header.source)
        if (responderKey == null) {
            logger.info("Received ${ResponderSessionMessage.ResponderHandshake::class.java.simpleName} from peer " +
                    "(${message.header.source}) which is not in the network map.")
            return
        }
        session.validatePeerHandshakeMessage(message.message, responderKey)
        activeSessions[message.header.source] = session.getSession()
        pendingSessions.remove(message.header.source)
        queuedMessagesPendingSession[message.header.source]?.forEach { sendMessage(it) }
    }
}

data class Header(val source: Peer, val dest: Peer, val sessionId: String)

sealed class InitiatorSessionMessage(open val header: Header) {

    data class InitiatorHello(override val header: Header, val message: InitiatorHelloMessage)
        : InitiatorSessionMessage(header)

    data class Step2Message(override val header: Header,
                            val initiatorHelloMsg: InitiatorHelloMessage,
                            val responderHelloMsg: ResponderHelloMessage,
                            val privateKey: ByteArray,
                            val publicKey: ByteArray): InitiatorSessionMessage(header) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Step2Message

            if (header != other.header) return false
            if (initiatorHelloMsg != other.initiatorHelloMsg) return false
            if (responderHelloMsg != other.responderHelloMsg) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
            if (!publicKey.contentEquals(other.publicKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = header.hashCode()
            result = 31 * result + initiatorHelloMsg.hashCode()
            result = 31 * result + responderHelloMsg.hashCode()
            result = 31 * result + privateKey.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }

    data class InitiatorHandshake(override val header: Header, val message: InitiatorHandshakeMessage)
        : InitiatorSessionMessage(header)

}

sealed class ResponderSessionMessage(open val header: Header) {
    data class ResponderHello(override val header: Header, val message: ResponderHelloMessage):
        ResponderSessionMessage(header)

    data class ResponderHandshake(override val header: Header, val message: ResponderHandshakeMessage):
        ResponderSessionMessage(header)
}
typealias Peer = String

data class SessionMessage(val payload: ByteArray, val dest: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionMessage

        if (!payload.contentEquals(other.payload)) return false
        if (dest != other.dest) return false

        return true
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + dest.hashCode()
        return result
    }
}

data class InboundSessionMessage(val payload: ByteArray, val source: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InboundSessionMessage

        if (!payload.contentEquals(other.payload)) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + source.hashCode()
        return result
    }

}

data class AuthenticatedMessage(val source: Peer, val dest: Peer, val payload: ByteArray, val mac: AuthenticationResult) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuthenticatedMessage

        if (source != other.source) return false
        if (dest != other.dest) return false
        if (!payload.contentEquals(other.payload)) return false
        if (mac != other.mac) return false

        return true
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + dest.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + mac.hashCode()
        return result
    }
}