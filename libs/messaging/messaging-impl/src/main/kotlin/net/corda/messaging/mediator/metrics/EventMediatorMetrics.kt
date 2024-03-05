package net.corda.messaging.mediator.metrics

import net.corda.messaging.constants.MetricsConstants
import net.corda.metrics.CordaMetrics

class EventMediatorMetrics(
    mediatorName: String,
) {
    val processorTimer = CordaMetrics.Metric.Messaging.MessageProcessorTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.BATCH_PROCESS_OPERATION)
        .build()

    val pollTimer = CordaMetrics.Metric.Messaging.ConsumerPollTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val commitTimer = CordaMetrics.Metric.Messaging.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val consumerProcessorFailureCounter = CordaMetrics.Metric.Messaging.ConsumerProcessorFailureCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val eventProcessorFailureCounter = CordaMetrics.Metric.Messaging.EventProcessorFailureCount.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    /**
     * This metric records how long an asynchronous event was waiting for a prior event in the same batch
     * to complete before it could begin processing.
     */
    val asyncEventWaitTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.ASYNC_EVENT_WAIT_TIME)
        .build()

    /**
     * Records how long it takes to process a batch of events for a single state
     */
    val processAsyncEventsTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PROCESS_ASYNC_EVENTS_TIME)
        .build()

    /**
     * Records the time between supplying an async event call, and the call being executed
     */
    val processSingleAsyncEventWaitTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PROCESS_SINGLE_ASYNC_EVENT_WAIT_TIME)
        .build()

    /**
     * Records how long it takes to process a single asynchronous event for a given state
     */
    val processSingleAsyncEventTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PROCESS_SINGLE_ASYNC_EVENT_TIME)
        .build()

    /**
     * Records how long it takes to process the batch of synchronous event responses for a given event within a state
     */
    val processSyncEventsTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PROCESS_SYNC_EVENTS_TIME)
        .build()

    /**
     * Records how long it takes to process a given synchronous event within a batch
     */
    val processSingleSyncEventTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PROCESS_SINGLE_SYNC_EVENT_TIME)
        .build()

    /**
     * Records the time between supplying an async state update call, and the call being executed
     */
    val persistStateWaitTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PERSIST_STATE_WAIT_TIME)
        .build()

    /**
     * Records how long it takes to persist a state following event processing
     */
    val persistStateTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.PERSIST_STATE_TIME)
        .build()

    /**
     * Record the time between supplying a batch of async events for processing, and the processing taking place
     */
    val sendAsyncEventsWaitTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.SEND_ASYNC_EVENTS_WAIT_TIME)
        .build()

    /**
     * Records how long it takes to send a batch of asynchronous outputs for a given state
     */
    val sendAsyncEventsTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.SEND_ASYNC_EVENTS_TIME)
        .build()

    /**
     * Records how long it takes to send a single asynchronous output for a given state
     */
    val sendSingleAsyncEventTimer = CordaMetrics.Metric.Messaging.AsyncMessageProcessingTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .withTag(CordaMetrics.Tag.OperationName, MetricsConstants.SEND_SINGLE_ASYNC_EVENT_TIME)
        .build()
}
