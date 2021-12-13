package net.corda.flow.manager

interface FlowEventExecutor{
    fun execute(): FlowResult
}