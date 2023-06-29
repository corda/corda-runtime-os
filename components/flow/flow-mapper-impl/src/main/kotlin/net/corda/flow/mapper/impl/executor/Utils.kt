package net.corda.flow.mapper.impl.executor

import net.corda.session.manager.Constants

/**
 * Toggle the [sessionId] to that of the other party and return it.
 * Initiating party sessionId will be a random UUID.
 * Initiated party sessionId will be the initiating party session id with a suffix of "-INITIATED" added.
 * @return the toggled session id
 */
internal fun toggleSessionId(sessionId: String): String {
    return if (sessionId.endsWith(Constants.INITIATED_SESSION_ID_SUFFIX)) {
        sessionId.removeSuffix(Constants.INITIATED_SESSION_ID_SUFFIX)
    } else {
        "$sessionId${Constants.INITIATED_SESSION_ID_SUFFIX}"
    }
}

