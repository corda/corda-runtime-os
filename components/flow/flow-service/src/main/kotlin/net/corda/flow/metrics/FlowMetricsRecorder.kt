package net.corda.flow.metrics

@Suppress("TooManyFunctions")
interface FlowMetricsRecorder {
    fun recordFlowEventLag(lagMilli: Long, flowEventType: String)
    fun recordFlowStartLag(lagMilli: Long)
    fun recordFlowSuspensionCompletion(flowName: String, isSubFlow: Boolean, operationName: String, executionTimeMilli: Long)
    fun recordFiberExecution(flowName: String, isSubFlow: Boolean, executionTimeMillis: Long)
    fun recordPipelineExecution(flowName: String, isSubFlow: Boolean, executionTimeMillis: Long, flowEventType: String)
    fun recordTotalPipelineExecutionTime(flowName: String, isSubFlow: Boolean, executionTimeMillis: Long)
    fun recordTotalFiberExecutionTime(flowName: String, isSubFlow: Boolean, executionTimeMillis: Long)
    fun recordTotalSuspensionTime(flowName: String, isSubFlow: Boolean, executionTimeMillis: Long)
    fun recordFlowCompletion(flowName: String, executionTimeMillis: Long, runTimeMillis: Long, completionStatus: String)
    fun recordFlowSessionMessagesReceived(flowName: String, isSubFlow: Boolean, flowEventType: String)
    fun recordFlowSessionMessagesSent(flowName: String, isSubFlow: Boolean, flowEventType: String)
    fun recordTotalEventsProcessed(flowName: String, isSubFlow: Boolean, eventsProcessed: Long)
    fun recordTotalFiberSuspensions(flowName: String, isSubFlow: Boolean, fiberSuspensions: Long)
    fun recordSubFlowCompletion(subFlowName: String, runTimeMillis: Long, completionStatus: String)
    fun recordFlowSessionMessagesReplayed(flowName: String, isSubFlow: Boolean, flowEventType: String)

}