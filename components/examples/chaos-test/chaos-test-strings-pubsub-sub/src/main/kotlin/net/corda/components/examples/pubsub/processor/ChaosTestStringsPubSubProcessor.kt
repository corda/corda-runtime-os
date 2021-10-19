package net.corda.components.examples.pubsub.processor

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class ChaosTestStringsPubSubProcessor : PubSubProcessor<String, String> {

    private companion object {
        val log = contextLogger()
    }

    override fun onNext(event: Record<String, String>) {
        log.info("PubSub Processor: Record key/value  ${event.key}/${event.value}")
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java
}