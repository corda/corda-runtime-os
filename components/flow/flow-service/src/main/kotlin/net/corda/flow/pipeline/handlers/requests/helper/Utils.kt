package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.session.manager.Constants

/**
 * check if an event is sent by Initiating identity
 * @param sessionEvent input session event to get identity info from
 * @return [Boolean] is this event sent by the Initiating identity
 */
fun isInitiatingIdentity(sessionId: String): Boolean {
    return !sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)
}

