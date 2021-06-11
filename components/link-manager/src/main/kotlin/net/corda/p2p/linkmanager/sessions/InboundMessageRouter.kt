package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.FlowMessage
import net.corda.p2p.crypto.GatewayToLinkManagerMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.LinkManagerToGatewayMessage
import net.corda.p2p.crypto.ResponderHandshakeMessage
import net.corda.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.Step2Message
import org.slf4j.LoggerFactory

class InboundMessageRouter(
    private val initiator: SessionManagerInitiator,
    private val responder: SessionManagerResponder) {

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    fun routeMessage(message: GatewayToLinkManagerMessage): Pair<List<FlowMessage>, List<LinkManagerToGatewayMessage>> {
        return when (val payload = message.payload) {
            is AuthenticatedDataMessage -> {
                responder.processAuthenticatedMessage(payload)
                Pair(responder.getQueuedInboundMessages(), responder.getQueuedOutboundMessages())
            }
            is InitiatorHandshakeMessage -> {
                responder.processInitiatorHandshake(payload)
                Pair(responder.getQueuedInboundMessages(), responder.getQueuedOutboundMessages())
            }
            is Step2Message -> {
                responder.processStep2Message(payload)
                Pair(responder.getQueuedInboundMessages(), responder.getQueuedOutboundMessages())
            }
            is ResponderHelloMessage -> {
                initiator.processResponderHello(payload)
                Pair(emptyList(), initiator.getQueuedOutboundMessages())
            }
            is ResponderHandshakeMessage -> {
                initiator.processResponderHandshake(payload)
                Pair(emptyList(), initiator.getQueuedOutboundMessages())
            }
            else -> {
                logger.warn("Cannot process message of type: ${payload::class.java}.")
                Pair(emptyList(), emptyList())
            }
        }
    }

}