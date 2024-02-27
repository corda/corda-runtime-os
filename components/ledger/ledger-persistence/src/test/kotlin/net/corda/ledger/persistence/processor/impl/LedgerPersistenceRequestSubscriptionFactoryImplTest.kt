package net.corda.ledger.persistence.processor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class LedgerPersistenceRequestSubscriptionFactoryImplTest {
    @Test
    fun `factory creates rpc subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()

        val expectedSubscription = mock<RPCSubscription<LedgerPersistenceRequest, FlowEvent>>()

        whenever(
            subscriptionFactory.createHttpRPCSubscription(
                any(),
                any<SyncRPCProcessor<LedgerPersistenceRequest, FlowEvent>>(),
            )
        ).thenReturn(expectedSubscription)

        val target = LedgerPersistenceRequestSubscriptionFactoryImpl(mock(), subscriptionFactory, mock(), mock(), mock())
        assertThat(target).isNotNull
        val result = target.createRpcSubscription()
        assertThat(result).isNotNull
        assertThat(result).isSameAs(expectedSubscription)
    }
}
