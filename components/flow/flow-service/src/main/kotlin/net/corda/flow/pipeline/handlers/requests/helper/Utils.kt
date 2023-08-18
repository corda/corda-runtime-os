package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.data.flow.event.SessionEvent
import net.corda.session.manager.Constants

fun isInitiatedIdentity(sessionEvent: SessionEvent): Boolean {
    return sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)
}