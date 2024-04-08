package net.corda.p2p.linkmanager.sessions.utils

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.StateManagerAction
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper

internal data class OutboundMessageContext<T>(
    val trace: T,
    val message: AuthenticatedMessageAndKey,
)

internal data class OutboundSessionMessageContext<T>(
    val sessionId: String,
    val outboundSessionMessage: OutboundSessionMessage,
    val trace: T,
)

internal data class InboundSessionMessageContext<T>(
    val sessionId: String,
    val inboundSessionMessage: InboundSessionMessage,
    val trace: T,
)

internal data class OutboundMessageResults<T>(
    val key: String?,
    val messages: Collection<OutboundMessageContext<T>>,
    val action: StateManagerAction?,
    val sessionState: SessionManager.SessionState,
)

internal data class OutboundMessageState<T>(
    val key: String?,
    val stateManagerSessionState: StateManagerWrapper.StateManagerSessionState?,
    val messages: Collection<OutboundMessageContext<T>>,
) {
    val first by lazy {
        messages.first()
    }
    val others by lazy {
        messages.drop(1)
    }

    fun toResults(
        sessionState: SessionManager.SessionState,
    ): Collection<OutboundMessageResults<T>> {
        return listOf(
            OutboundMessageResults(
                key = this.key,
                messages = this.messages,
                action = null,
                sessionState = sessionState,
            ),
        )
    }

    fun toResultsFirstAndOther(
        firstState: SessionManager.SessionState,
        otherStates: SessionManager.SessionState,
        action: StateManagerAction,
    ): Collection<OutboundMessageResults<T>> {
        val firstResult = OutboundMessageResults(
            key = this.key,
            messages = listOf(first),
            action = action,
            sessionState = firstState,
        )
        return if (others.isEmpty()) {
            listOf(firstResult)
        } else {
            listOf(
                firstResult,
                OutboundMessageResults(
                    key = this.key,
                    messages = others,
                    action = null,
                    sessionState = otherStates,
                ),
            )
        }
    }
}

internal data class Result(
    val message: LinkOutMessage?,
    val stateAction: StateManagerAction,
    val sessionToCache: Session?,
)

internal data class TraceableResult<T>(
    val traceable: T,
    val result: Result?,
)

internal sealed interface InboundSessionMessage {
    data class InitiatorHelloMessage(
        val initiatorHelloMessage: net.corda.data.p2p.crypto.InitiatorHelloMessage,
    ) : InboundSessionMessage

    data class InitiatorHandshakeMessage(
        val initiatorHandshakeMessage: net.corda.data.p2p.crypto.InitiatorHandshakeMessage,
    ) : InboundSessionMessage
}

internal sealed interface OutboundSessionMessage {
    data class ResponderHelloMessage(
        val responderHelloMessage: net.corda.data.p2p.crypto.ResponderHelloMessage,
    ) : OutboundSessionMessage

    data class ResponderHandshakeMessage(
        val responderHandshakeMessage: net.corda.data.p2p.crypto.ResponderHandshakeMessage,
    ) : OutboundSessionMessage
}