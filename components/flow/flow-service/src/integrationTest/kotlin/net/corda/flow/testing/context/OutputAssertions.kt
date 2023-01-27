package net.corda.flow.testing.context

import net.corda.data.flow.output.FlowStates
import net.corda.data.identity.HoldingIdentity

interface OutputAssertions {

    fun sessionAckEvents(
        vararg sessionIds: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    )

    fun sessionInitEvents(
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

    fun sessionErrorEvents(
        vararg sessionIds: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    )

    fun externalEvent(topic: String, key: Any, payload: Any)

    fun noExternalEvent(topic: String)

    fun scheduleFlowMapperCleanupEvents(vararg key: String)

    fun flowDidNotResume()

    fun flowResumedWith(value: Any?)

    fun <T : Throwable> flowResumedWithError(exceptionClass: Class<T>)

    fun wakeUpEvent()

    fun noWakeUpEvent()

    fun hasPendingUserException()

    fun noPendingUserException()

    fun noFlowEvents()

    fun checkpointHasRetry(expectedCount: Int)

    fun checkpointDoesNotHaveRetry()

    fun flowStatus(state: FlowStates, result: String? = null, errorType: String? = null, errorMessage:String? = null)

    fun nullStateRecord()

    fun markedForDlq()

    fun entityRequestSent(expectedRequestPayload: Any)

    fun noEntityRequestSent()

    fun flowKilledStatus(flowTerminatedReason: String)
}

inline fun <reified T: Throwable> OutputAssertions.flowResumedWithError() = flowResumedWithError(T::class.java)