package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.verification.TransactionVerificationRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class VerificationSubscriptionFactoryImplTest {
    @Test
    fun `factory creates kafka subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val config = mock<SmartConfig>()

        val expectedSubscription = mock<Subscription<String, TransactionVerificationRequest>>()
        val expectedSubscriptionConfig = SubscriptionConfig(
            "verification.ledger.processor",
            VERIFICATION_LEDGER_PROCESSOR_TOPIC
        )

        whenever(
            subscriptionFactory.createDurableSubscription(
                eq(expectedSubscriptionConfig),
                any<DurableProcessor<String, TransactionVerificationRequest>>(),
                eq(config),
                eq(null)
            )
        ).thenReturn(expectedSubscription)

        val target = VerificationSubscriptionFactoryImpl(mock(), subscriptionFactory, mock(), mock())

        val result = target.create(config)

        assertThat(result).isSameAs(expectedSubscription)
    }

    @Test
    fun `factory creates rpc subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()

        val expectedSubscription = mock<RPCSubscription<TransactionVerificationRequest, FlowEvent>>()

        whenever(
            subscriptionFactory.createHttpRPCSubscription(
                any(),
                any<SyncRPCProcessor<TransactionVerificationRequest, FlowEvent>>()
            )
        ).thenReturn(expectedSubscription)

        val target = VerificationSubscriptionFactoryImpl(mock(), subscriptionFactory, mock(), mock())
        assertThat(target).isNotNull
        val result = target.createRpcSubscription()
        assertThat(result).isNotNull
        assertThat(result).isSameAs(expectedSubscription)
    }
}
