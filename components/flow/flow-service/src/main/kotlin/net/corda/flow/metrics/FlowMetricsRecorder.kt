package net.corda.flow.metrics

interface FlowMetricsRecorder {
    fun recordFlowEventLag(lagMilli: Long, flowEventType: String)
    fun recordFlowStartLag(lagMilli: Long)
    fun recordFlowSuspensionCompletion(flowName: String, operationName: String, executionTimeMilli: Long)
    fun recordFiberExecution(flowName: String, executionTimeMillis: Long)
    fun recordPipelineExecution(flowName: String, executionTimeMillis: Long, flowEventType: String)
    fun recordTotalPipelineExecutionTime(flowName: String, executionTimeMillis: Long)
    fun recordTotalFiberExecutionTime(flowName: String, executionTimeMillis: Long)
    fun recordTotalSuspensionTime(flowName: String, executionTimeMillis: Long)
    fun recordFlowCompletion(flowName: String, executionTimeMillis: Long, runTimeMillis: Long, completionStatus:String)
    fun recordFlowSessionMessagesReceived(flowName: String, flowEventType: String)
    fun recordFlowSessionMessagesSent(flowName: String, flowEventType: String)
    fun recordTotalEventsProcessed(flowName: String, eventsProcessed: Long)
    fun recordTotalFiberSuspensions(flowName: String, fiberSuspensions: Long)
    fun recordSubFlowCompletion(subFlowName: String, runTimeMillis: Long, completionStatus: String)
}