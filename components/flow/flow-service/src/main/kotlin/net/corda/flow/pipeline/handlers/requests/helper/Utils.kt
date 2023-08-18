package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.data.flow.event.SessionEvent
import net.corda.session.manager.Constants

/**
 * check if an event is sent by Initiated or Initiating identity
 * @param sessionEvent input session event to get identity info from
 * @return [Boolean] is this event sent by the Initiated identity
 */
fun isInitiatedIdentity(sessionEvent: SessionEvent): Boolean {
    return sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)
}