package net.corda.messaging.mediator.metrics

import net.corda.metrics.CordaMetrics

class EventMediatorMetrics {
    fun timer(topic: String, operationName: String) = CordaMetrics.Metric.Messaging.MediatorTime.builder()
        .withTag(CordaMetrics.Tag.Topic, topic)
        .withTag(CordaMetrics.Tag.OperationName, operationName)
        .build()

    fun recordSize(topic: String, operationName: String, size: Int) =
        CordaMetrics.Metric.Messaging.ConsumerPollSize.builder()
            .withTag(CordaMetrics.Tag.Topic, topic)
            .withTag(CordaMetrics.Tag.OperationName, operationName)
            .build()
            .record(size.toDouble())
}