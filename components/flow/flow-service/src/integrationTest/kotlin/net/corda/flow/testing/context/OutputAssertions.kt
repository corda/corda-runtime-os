package net.corda.flow.testing.context

import net.corda.data.flow.output.FlowStates
import net.corda.data.identity.HoldingIdentity

interface OutputAssertions {

    fun sessionAckEvent(sessionId: String, initiatingIdentity: HoldingIdentity? = null, initiatedIdentity: HoldingIdentity? = null)

    fun flowDidNotResume()

    fun flowResumedWithSessionData(vararg sessionData: Pair<String, ByteArray>)

    fun wakeUpEvent()

    fun flowStatus(state: FlowStates, result: String? = null, error: Exception? = null)

    fun nullStateRecord()
}