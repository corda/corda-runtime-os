package net.corda.messaging.mediator.metrics

import net.corda.metrics.CordaMetrics

class EventMediatorMetrics(
    private val mediatorName: String,
) {
    fun timer(topic: String, operationName: String) = CordaMetrics.Metric.Messaging.MediatorTime.builder()
        .withTag(CordaMetrics.Tag.Topic, topic)
        .withTag(CordaMetrics.Tag.OperationName, operationName)
        .build()

    fun recordPollSize(topic: String, size: Int) =
        CordaMetrics.Metric.Messaging.ConsumerPollSize.builder()
            .withTag(CordaMetrics.Tag.Topic, topic)
            .build()
            .record(size.toDouble())
}