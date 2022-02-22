package net.corda.virtualnode.write.db.impl.tests.writer

import com.typesafe.config.ConfigFactory
import net.corda.data.virtualnode.VirtualNodeCreationRequest
import net.corda.data.virtualnode.VirtualNodeCreationResponse
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.VirtualNode.Companion.VIRTUAL_NODE_CREATION_REQUEST_TOPIC
import net.corda.virtualnode.write.db.impl.writer.CLIENT_NAME_DB
import net.corda.virtualnode.write.db.impl.writer.CLIENT_NAME_RPC
import net.corda.virtualnode.write.db.impl.writer.GROUP_NAME
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [VirtualNodeWriterFactory]. */
class VirtualNodeWriterFactoryTests {
    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    /** Returns a mock [SubscriptionFactory]. */
    private fun getSubscriptionFactory() = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription<Any, Any>(any(), any(), any())).doReturn(mock())
    }

    /** Returns a mock [PublisherFactory]. */
    private fun getPublisherFactory() = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(mock())
    }

    @Test
    fun `factory does not start the virtual node writer`() {
        val virtualNodeWriterFactory = VirtualNodeWriterFactory(getSubscriptionFactory(), getPublisherFactory(), mock(), mock(), mock(), mock())
        val virtualNodeWriter = virtualNodeWriterFactory.create(mock(), 0)
        assertFalse(virtualNodeWriter.isRunning)
    }

    @Test
    fun `factory creates a publisher with the correct configuration`() {
        val expectedPublisherConfig = PublisherConfig(CLIENT_NAME_DB, 777)
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))

        val publisherFactory = getPublisherFactory()
        val virtualNodeWriterFactory = VirtualNodeWriterFactory(getSubscriptionFactory(), publisherFactory, mock(), mock(), mock(), mock())
        virtualNodeWriterFactory.create(expectedConfig, expectedPublisherConfig.instanceId!!)

        verify(publisherFactory).createPublisher(expectedPublisherConfig, expectedConfig)
    }

    @Test
    fun `factory creates an RPC subscription with the correct configuration`() {
        val expectedRPCConfig = RPCConfig(
            GROUP_NAME,
            CLIENT_NAME_RPC,
            VIRTUAL_NODE_CREATION_REQUEST_TOPIC,
            VirtualNodeCreationRequest::class.java,
            VirtualNodeCreationResponse::class.java,
        )
        val expectedConfig = configFactory.create(ConfigFactory.parseMap(mapOf("dummyKey" to "dummyValue")))

        val subscriptionFactory = getSubscriptionFactory()
        val virtualNodeWriterFactory = VirtualNodeWriterFactory(subscriptionFactory, getPublisherFactory(), mock(), mock(), mock(), mock())
        virtualNodeWriterFactory.create(expectedConfig, 777)

        verify(subscriptionFactory).createRPCSubscription(eq(expectedRPCConfig), eq(expectedConfig), any())
    }
}
