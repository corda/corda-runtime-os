package net.corda.entityprocessor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class EntityRequestSubscriptionFactoryImplTest {

    @Test
    fun `factory creates rpc subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()

        val expectedSubscription = mock<RPCSubscription<EntityRequest, FlowEvent>>()

        whenever(
            subscriptionFactory.createHttpRPCSubscription(
                any(),
                any<SyncRPCProcessor<EntityRequest, FlowEvent>>(),
            )
        ).thenReturn(expectedSubscription)

        val target = EntityRequestSubscriptionFactoryImpl(mock(), subscriptionFactory, mock(), mock())
        Assertions.assertThat(target).isNotNull
        val result = target.createRpcSubscription()
        Assertions.assertThat(result).isNotNull
        Assertions.assertThat(result).isSameAs(expectedSubscription)
    }

}
