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
}