package net.corda.flow.utils

const val INITIATED_SESSION_ID_SUFFIX = "-INITIATED"

fun isInitiatedIdentity(sessionId: String) : Boolean {
    return sessionId.contains(INITIATED_SESSION_ID_SUFFIX)
}

fun isInitiatingIdentity(sessionId: String) : Boolean {
    return !sessionId.contains(INITIATED_SESSION_ID_SUFFIX)
}