package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.InvalidMac
import net.corda.p2p.crypto.protocol.api.Mode
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SessionManagerResponder(
    private val mode: Mode,
    private val us: Peer,
    private val networkMap: (Peer) -> PublicKey?,
    private val signingFn: (ByteArray) -> ByteArray
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)
    private val queuedOutboundMessages = ConcurrentLinkedQueue<Any>()
    private val queuedInboundMessages =  ConcurrentLinkedQueue<InboundSessionMessage>()
    private val pendingSessions = ConcurrentHashMap<String, AuthenticationProtocolResponder>()
    private val activeSessions = ConcurrentHashMap<String, AuthenticatedSession>()

    fun processAuthenticatedMessage(message: AuthenticatedMessage) {
        val session = activeSessions[message.mac.header.sessionId]
        if (session != null) {
            try {
                session.validateMac(message.mac.header, message.payload, message.mac.mac)
            } catch (expection: InvalidMac) {
                logger.warn("MAC check failed for message for session ${message.mac.header.sessionId}.")
                return
            }
            queuedInboundMessages.add(InboundSessionMessage(message.payload, message.source))
        } else {
            logger.warn("Received message of type ${AuthenticatedMessage::class.java.simpleName}" +
                " but there is no session with id: ${message.mac.header.sessionId}")
        }
    }

    fun processSessionMessage(message: InitiatorSessionMessage) {
        when (message) {
            is InitiatorSessionMessage.InitiatorHandshake -> processInitiatorHandshake(message)
            is InitiatorSessionMessage.Step2Message -> processStep2Message(message)
            is InitiatorSessionMessage.InitiatorHello -> processInitiatorHello()
        }
    }

    fun getQueuedOutboundMessage(): Any? {
        return queuedOutboundMessages.poll()
    }

    fun getQueuedInboundMessage(): InboundSessionMessage? {
        return queuedInboundMessages.poll()
    }

    private fun processInitiatorHello() {
        throw IllegalArgumentException("Received a message of type ${InitiatorSessionMessage.InitiatorHello::class.java.simpleName} " +
            "this message should be processed by the gateway.")
    }

    private fun processStep2Message(message: InitiatorSessionMessage.Step2Message) {
        val session = AuthenticationProtocolResponder.fromStep2(message.header.sessionId,
            listOf(Mode.AUTHENTICATION_ONLY),
            message.initiatorHelloMsg,
            message.responderHelloMsg,
            message.privateKey,
            message.publicKey)
        session.generateHandshakeSecrets()
        pendingSessions[message.header.sessionId] = session
    }

    private fun processInitiatorHandshake(message: InitiatorSessionMessage.InitiatorHandshake) {
        val session = pendingSessions[message.header.sessionId]
        if (session == null) {
            logger.warn("Received ${InitiatorSessionMessage.InitiatorHandshake::class.java::getSimpleName} from peer " +
                "${message.header.source} but there is no pending session. The message was discarded.")
            return
        }
        val ourKey = networkMap(us)
        if (ourKey == null) {
            logger.warn("Could not find our identity ($us) in the network map.")
            return
        }
        val initiatorPublicKey = networkMap(message.header.source)
        if (initiatorPublicKey == null) {
            logger.info("Received ${InitiatorSessionMessage.InitiatorHandshake::class.java.simpleName} from peer " +
                "(${message.header.source}) which is not in the network map.")
            return
        }
        session.validatePeerHandshakeMessage(message.message) { initiatorPublicKey }
        val response = session.generateOurHandshakeMessage(ourKey, signingFn)
        queuedOutboundMessages.add(ResponderSessionMessage.ResponderHandshake(Header(us, message.header.source, message.header.sessionId), response))
        activeSessions[message.header.sessionId] = session.getSession()
        pendingSessions.remove(message.header.sessionId)
    }
}