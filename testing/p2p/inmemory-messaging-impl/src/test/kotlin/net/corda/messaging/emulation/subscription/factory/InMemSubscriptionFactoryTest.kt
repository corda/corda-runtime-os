package net.corda.messaging.emulation.subscription.factory

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.subscription.compacted.InMemoryCompactedSubscription
import net.corda.messaging.emulation.subscription.durable.DurableSubscription
import net.corda.messaging.emulation.subscription.eventlog.EventLogSubscription
import net.corda.messaging.emulation.subscription.pubsub.PubSubSubscription
import net.corda.messaging.emulation.subscription.stateandevent.InMemoryStateAndEventSubscription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class InMemSubscriptionFactoryTest {
    private val factory = InMemSubscriptionFactory(mock(), mock(), mock())
    private val subscriptionConfig = SubscriptionConfig("topic", "group")

    @Test
    fun `createPubSubSubscription creates PubSubSubscription`() {
        assertThat(
            factory.createPubSubSubscription(
                subscriptionConfig,
                mock<PubSubProcessor<String, Long>>(),
                mock(),
            )
        ).isInstanceOf(PubSubSubscription::class.java)
    }

    @Test
    fun `createEventLogSubscription creates EventLogSubscription`() {
        assertThat(
            factory.createEventLogSubscription(
                subscriptionConfig,
                mock<EventLogProcessor<String, Long>>(),
                mock(),
                mock()
            )
        ).isInstanceOf(EventLogSubscription::class.java)
    }

    @Test
    fun `createCompactedSubscription creates InMemoryCompactedSubscription`() {
        assertThat(
            factory.createCompactedSubscription(
                subscriptionConfig,
                mock<CompactedProcessor<String, Long>>(),
                mock(),
            )
        ).isInstanceOf(InMemoryCompactedSubscription::class.java)
    }

    @Test
    fun `createStateAndEventSubscription creates InMemoryStateAndEventSubscription`() {
        assertThat(
            factory.createStateAndEventSubscription(
                subscriptionConfig,
                mock<StateAndEventProcessor<String, String, Long>>(),
                mock(),
            )
        ).isInstanceOf(InMemoryStateAndEventSubscription::class.java)
    }

    @Test
    fun `createDurableSubscription creates DurableSubscription`() {
        assertThat(
            factory.createDurableSubscription(
                subscriptionConfig,
                mock<DurableProcessor<String, Long>>(),
                mock(),
                mock(),
            )
        ).isInstanceOf(DurableSubscription::class.java)
    }
}
