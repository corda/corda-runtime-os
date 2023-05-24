package net.corda.flow.metrics

interface FlowMetricsRecorder {
    fun recordFlowEventLag(lagMilli: Long, flowEventType: String)
    fun recordFlowStartLag(lagMilli: Long)
    fun recordFlowSuspensionCompletion(operationName: String, executionTimeMilli: Long)
    fun recordFiberExecution(executionTimeMillis: Long)
    fun recordPipelineExecution(executionTimeMillis: Long, flowEventType: String)
    fun recordTotalPipelineExecutionTime(executionTimeMillis: Long)
    fun recordTotalFiberExecutionTime(executionTimeMillis: Long)
    fun recordTotalSuspensionTime(executionTimeMillis: Long)
    fun recordFlowCompletion(executionTimeMillis: Long, completionStatus:String)
    fun recordFlowSessionMessagesReceived(flowEventType: String)
    fun recordFlowSessionMessagesSent(flowEventType: String)
    fun recordTotalEventsProcessed(eventsProcessed: Long)
    fun recordTotalFiberSuspensions(fiberSuspensions: Long)
}