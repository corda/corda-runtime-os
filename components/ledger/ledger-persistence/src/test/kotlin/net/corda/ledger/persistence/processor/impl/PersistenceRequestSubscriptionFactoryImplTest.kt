package net.corda.ledger.persistence.processor.impl

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class PersistenceRequestSubscriptionFactoryImplTest {
    @Test
    fun `factory creates subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val config = mock<SmartConfig>()

        val expectedSubscription = mock<Subscription<String, LedgerPersistenceRequest>>()
        val expectedSubscriptionConfig = SubscriptionConfig(
            "persistence.ledger.processor",
            PERSISTENCE_LEDGER_PROCESSOR_TOPIC
        )

        whenever(
            subscriptionFactory.createDurableSubscription(
                eq(expectedSubscriptionConfig),
                any<DurableProcessor<String, LedgerPersistenceRequest>>(),
                eq(config),
                eq(null)
            )
        ).thenReturn(expectedSubscription)

        val target = PersistenceRequestSubscriptionFactoryImpl(subscriptionFactory, mock(), mock(), mock())

        val result = target.create(config)

        assertThat(result).isSameAs(expectedSubscription)
    }
}
