package net.corda.flow.testing.context

import net.corda.data.flow.output.FlowStates

interface OutputAssertions {
    fun sessionAckEvent(
        flowId: String,
        sessionId: String,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity? = null,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity? = null
    )

    fun flowDidNotResume()

    fun flowResumedWithSessionData(vararg sessionData: Pair<String, ByteArray>)

    fun wakeUpEvent()

    fun flowStatus(state: FlowStates, result: String? = null, error: Exception? = null)

    fun nullStateRecord()
}