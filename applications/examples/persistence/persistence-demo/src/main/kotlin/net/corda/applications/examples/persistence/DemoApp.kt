package net.corda.applications.examples.persistence

import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.osgi.api.Application
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component
class DemoApp @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
)  : Application {

    companion object {
        val log: Logger = contextLogger()
    }

    override fun startup(args: Array<String>) {
        log.info("Starting persistence demo application...")
        println(subscriptionFactory)
    }

    override fun shutdown() {
        log.info("Shutting down persistence demo application...")
    }
}