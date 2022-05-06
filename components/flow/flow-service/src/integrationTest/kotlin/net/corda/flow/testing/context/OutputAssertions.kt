package net.corda.flow.testing.context

import net.corda.data.flow.output.FlowStates
import net.corda.data.identity.HoldingIdentity

interface OutputAssertions {

    fun sessionAckEvents(
        vararg sessionIds: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    )

    fun sessionDataEvents(
        vararg sessionToPayload: Pair<String, ByteArray>,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    )

    fun sessionCloseEvents(
        vararg sessionIds: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    )

    fun flowDidNotResume()

    fun <T> flowResumedWith(value: T)

    fun wakeUpEvent()

    fun flowStatus(state: FlowStates, result: String? = null, error: Exception? = null)

    fun nullStateRecord()
}