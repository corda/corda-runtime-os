package net.corda.flow.manager

interface FlowEventExecutorFactory{
    fun create(flowMetaData: FlowMetaData): FlowEventExecutor
}