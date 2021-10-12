package net.corda.tools.bundle.cleanup

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [DemoConsumer::class])
class DemoConsumer @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriberFactory: SubscriptionFactory
): CompactedProcessor<String, String> {
    companion object {
        private val log = contextLogger()
    }

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    private val subscription: CompactedSubscription<String, String>

    init {
        val config = ConfigFactory.parseMap(mapOf(
            "messaging.kafka.common.bootstrap.servers" to "localhost:9093", // TODO - Stop hardcoding this.
        ))
        // TODO - Move constants to constants file.
        subscription = subscriberFactory.createCompactedSubscription(
            SubscriptionConfig(
                "joel-group",
                "bundle-cleanup-bundle-cleanup-topic"
            ), this, config)
    }

    fun start() {
        subscription.start()
    }

    override fun onSnapshot(currentData: Map<String, String>) = Unit

    override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
        log.info("JJJ - New record consumed: $newRecord")
    }
}