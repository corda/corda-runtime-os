package net.corda.flow.metrics

interface FlowMetricsRecord {
    fun recordFlowEventLag(lagMilli: Long)
    fun recordFlowStartLag(lagMilli: Long)
    fun recordFlowSuspensionCompletion(operationName: String, executionTimeMilli: Long)
    fun recordFiberExecution(executionTimeMillis: Long)
    fun recordPipelineExecution(executionTimeMillis: Long)
    fun recordTotalPipelineExecutionTime(executionTimeMillis: Long)
    fun recordTotalFiberExecutionTime(executionTimeMillis: Long)
    fun recordTotalSuspensionTime(executionTimeMillis: Long)
    fun recordFlowCompletion(executionTimeMillis: Long, completionStatus:String)
}