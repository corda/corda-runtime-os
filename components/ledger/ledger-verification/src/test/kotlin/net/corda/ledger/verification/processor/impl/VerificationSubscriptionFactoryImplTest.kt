package net.corda.ledger.verification.processor.impl

import net.corda.ledger.utxo.contract.verification.VerifyContractsRequest
import net.corda.ledger.utxo.contract.verification.VerifyContractsRequestRedelivery
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Verification.Companion.VERIFICATION_LEDGER_PROCESSOR_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class VerificationSubscriptionFactoryImplTest {
    @Test
    fun `factory creates subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val config = mock<SmartConfig>()

        val expectedSubscription = mock<StateAndEventSubscription<String, VerifyContractsRequestRedelivery, VerifyContractsRequest>>()
        val expectedSubscriptionConfig = SubscriptionConfig(
            "verification.ledger.processor",
            VERIFICATION_LEDGER_PROCESSOR_TOPIC
        )

        whenever(
            subscriptionFactory.createStateAndEventSubscription(
                eq(expectedSubscriptionConfig),
                any<StateAndEventProcessor<String, VerifyContractsRequestRedelivery, VerifyContractsRequest>>(),
                eq(config),
                any()
            )
        ).thenReturn(expectedSubscription)

        val target = VerificationSubscriptionFactoryImpl(subscriptionFactory, mock(), mock(), mock())

        val result = target.create(config)

        assertThat(result).isSameAs(expectedSubscription)
    }
}
