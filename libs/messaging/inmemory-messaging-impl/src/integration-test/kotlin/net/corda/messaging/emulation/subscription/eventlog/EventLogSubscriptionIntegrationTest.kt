package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class EventLogSubscriptionIntegrationTest {
    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @Test
    fun testEventLogsSubscription() {
        println("QQQ $subscriptionFactory")
        assertThat("a!").isEqualTo("a!")
    }
}
