package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTruststore
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class TrustStoresPublisherTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTruststore>>()
    private val subscription = mock<CompactedSubscription<String, GatewayTruststore>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any(),
            )
        } doReturn subscription
    }
    private val publishedRecords = argumentCaptor<List<Record<String, GatewayTruststore>>>()
    private val publisher = mock<Publisher> {
        on { publish(publishedRecords.capture()) } doReturn emptyList()
    }
    private val lifecycleEventHandler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            lifecycleEventHandler.firstValue.processEvent(it.getArgument(0), mock())
        }
    }
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), lifecycleEventHandler.capture()) } doReturn coordinator
    }
    private val configuration = mock<SmartConfig>()
    private val instanceId = 1
    private val publisherFactory = mock<PublisherFactory> {
        on {
            createPublisher(
                eq(PublisherConfig("linkmanager_truststore_writer")),
                eq(configuration)
            )
        } doReturn publisher
    }

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory, publisherFactory, lifecycleCoordinatorFactory, configuration, instanceId
    )

    @Nested
    inner class PublishGroupIfNeededTests {

        @Test
        fun `publishGroupIfNeeded will publish unpublished group`() {
            trustStoresPublisher.start()
            val certificates = listOf("one", "two")

            trustStoresPublisher.publishGroupIfNeeded("group1", certificates)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, "group1", GatewayTruststore(certificates)))
            )
        }

        @Test
        fun `publishGroupIfNeeded will not republish certificates`() {
            trustStoresPublisher.start()
            val certificates = listOf("one", "two")
            trustStoresPublisher.publishGroupIfNeeded("group1", certificates)

            trustStoresPublisher.publishGroupIfNeeded("group1", certificates)

            verify(publisher, times(1)).publish(any())
        }

        @Test
        fun `publishGroupIfNeeded will republish new certificates`() {
            trustStoresPublisher.start()
            val certificatesOne = listOf("one")
            val certificatesTwo = listOf("two")
            trustStoresPublisher.publishGroupIfNeeded("group1", certificatesOne)

            trustStoresPublisher.publishGroupIfNeeded("group1", certificatesTwo)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, "group1", GatewayTruststore(certificatesOne))),
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, "group1", GatewayTruststore(certificatesTwo))),
            )
        }

        @Test
        fun `publishGroupIfNeeded will wait for certificates to be published`() {
            trustStoresPublisher.start()
            val future = mock<CompletableFuture<Unit>>()
            whenever(publisher.publish(any())).doReturn(listOf(future))
            val certificates = listOf("one", "two")

            trustStoresPublisher.publishGroupIfNeeded("group1", certificates)

            verify(future).join()
        }
    }

    @Nested
    inner class ProcessorTests {
        @Test
        fun `onSnapshot mark the publisher as ready`() {
            trustStoresPublisher.start()
            val future = trustStoresPublisher.createResources(ResourcesHolder())

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(future.isDone).isTrue
        }

        @Test
        fun `onSnapshot save the data correctly`() {
            trustStoresPublisher.start()
            trustStoresPublisher.createResources(ResourcesHolder())
            val certificates = listOf(
                "one",
                "two",
                "three"
            )

            processor.firstValue.onSnapshot(
                mapOf(
                    "group one" to GatewayTruststore(
                        certificates
                    )
                )
            )

            trustStoresPublisher.publishGroupIfNeeded("group one", certificates)
            verify(publisher, never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            trustStoresPublisher.start()
            trustStoresPublisher.createResources(ResourcesHolder())
            val certificates = listOf(
                "one",
            )
            processor.firstValue.onSnapshot(
                mapOf(
                    "group one" to GatewayTruststore(
                        certificates
                    )
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", "group one",
                    null
                ),
                null, emptyMap()
            )

            trustStoresPublisher.publishGroupIfNeeded("group one", certificates)
            verify(publisher).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            trustStoresPublisher.start()
            trustStoresPublisher.createResources(ResourcesHolder())
            val certificates = listOf(
                "one",
            )

            processor.firstValue.onNext(
                Record(
                    "", "group one",
                    GatewayTruststore(
                        certificates
                    )
                ),
                null, emptyMap()
            )

            trustStoresPublisher.publishGroupIfNeeded("group one", certificates)
            verify(publisher, never()).publish(any())
        }
    }

    @Nested
    inner class CreateResourcesTests {
        @Test
        fun `createResources start the subscription`() {
            trustStoresPublisher.createResources(ResourcesHolder())

            verify(subscription).start()
        }

        @Test
        fun `createResources will not complete before the snapshot is ready`() {
            val future = trustStoresPublisher.createResources(ResourcesHolder())

            assertThat(future.isDone).isFalse
        }

        @Test
        fun `createResources will remember to close the subscription`() {
            val resources = ResourcesHolder()

            trustStoresPublisher.createResources(resources)

            resources.close()
            verify(subscription).close()
        }
    }
}
