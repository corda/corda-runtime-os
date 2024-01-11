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

    val groupTimer = CordaMetrics.Metric.Messaging.MessageGroupTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val loadTimer = CordaMetrics.Metric.Messaging.MessageLoadTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val persistTimer = CordaMetrics.Metric.Messaging.MessagePersistTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val sendAsyncTimer = CordaMetrics.Metric.Messaging.MessageSendAsyncTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val pollTimer = CordaMetrics.Metric.Messaging.ConsumerPollTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val commitTimer = CordaMetrics.Metric.Messaging.MessageCommitTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    val lagTimer = CordaMetrics.Metric.Messaging.MessageLagTime.builder()
        .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.EVENT_MEDIATOR_TYPE)
        .withTag(CordaMetrics.Tag.MessagePatternClientId, mediatorName)
        .build()

    fun recordPollSize(topic: String, size: Int) {
        CordaMetrics.Metric.Messaging.ConsumerPollSize.builder()
            .withTag(CordaMetrics.Tag.Topic, topic)
            .build()
            .record(size.toDouble())
    }
}