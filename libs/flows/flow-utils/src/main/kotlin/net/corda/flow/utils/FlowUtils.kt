package net.corda.flow.utils

import net.corda.data.flow.event.SessionEvent

const val INITIATED_SESSION_ID_SUFFIX = "-INITIATED"

fun isInitiatedParty(sessionEvent: SessionEvent) : Boolean {
    return sessionEvent.sessionId.contains(INITIATED_SESSION_ID_SUFFIX)
}