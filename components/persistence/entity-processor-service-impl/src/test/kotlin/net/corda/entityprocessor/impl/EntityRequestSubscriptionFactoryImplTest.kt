package net.corda.entityprocessor.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.persistence.EntityRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.persistence.common.PayloadChecker
import net.corda.persistence.common.exceptions.KafkaMessageSizeException
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal class EntityRequestSubscriptionFactoryImplTest {
    @Test
    fun `payload check throws if max bytes exceeded`() {
        val maxSize = 1024 * 10
        val bytes = ByteBuffer.wrap(ByteArray(maxSize + 1))
        assertThrows<KafkaMessageSizeException> { PayloadChecker(maxSize).checkSize(bytes) }
    }

    //TODO - add test for create and createRpcSubscription

    @Test
    fun `factory creates kafka subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val config = mock<SmartConfig>()

        val expectedSubscription = mock<Subscription<String, EntityRequest>>()
        val expectedSubscriptionConfig = SubscriptionConfig(
            "persistence.entity.processor",
            Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC
        )

        whenever(
            subscriptionFactory.createDurableSubscription(
                eq(expectedSubscriptionConfig),
                any<DurableProcessor<String, EntityRequest>>(),
                eq(config),
                eq(null)
            )
        ).thenReturn(expectedSubscription)

        val target = EntityRequestSubscriptionFactoryImpl(mock(), subscriptionFactory, mock(), mock())

        val result = target.create(config)

        Assertions.assertThat(result).isSameAs(expectedSubscription)
    }

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
