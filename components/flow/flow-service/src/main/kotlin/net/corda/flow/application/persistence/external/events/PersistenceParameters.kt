package net.corda.flow.application.persistence.external.events

data class PersistenceParameters(val request: Any, val debugLog: (requestId: String) -> String)