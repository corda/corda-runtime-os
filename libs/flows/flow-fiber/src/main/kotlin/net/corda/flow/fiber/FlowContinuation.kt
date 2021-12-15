package net.corda.flow.fiber

sealed class FlowContinuation {

    object Continue : FlowContinuation()

    data class Run(val value: Any? = Unit) : FlowContinuation()

    data class Error(val exception: Throwable) : FlowContinuation()
}