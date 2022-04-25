package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class TlsCertificatesPublisherTest {
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscription = mock<CompactedSubscription<String, GatewayTlsCertificates>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any(),
            )
        } doReturn subscription
    }
    private val publishedRecords = argumentCaptor<List<Record<String, GatewayTlsCertificates>>>()
    private val creteResources = AtomicReference<(resources: ResourcesHolder) -> CompletableFuture<Unit>>()
    private val mockDominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        creteResources.set(context.arguments()[2] as? (resources: ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val mockPublisher = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        whenever(mock.publish(publishedRecords.capture())).doReturn(emptyList())
        whenever(mock.isRunning).doReturn(true)
    }
    private val publisherFactory = mock<PublisherFactory>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val configuration = mock<SmartConfig>()
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val identityInfo = HostingMapListener.IdentityInfo(
        HoldingIdentity(
            "Alice",
            "Group1",
        ),
        listOf("one", "two"),
        "id1",
        "id2",
        mock()
    )

    private val publisher = TlsCertificatesPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
    )

    @AfterEach
    fun cleanUp() {
        mockPublisher.close()
        mockDominoTile.close()
        subscriptionDominoTile.close()
    }

    @Nested
    inner class IdentityAddedTests {

        @Test
        fun `identityAdded will publish unpublished identity`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())

            publisher.identityAdded(identityInfo)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(
                    Record(
                        GATEWAY_TLS_CERTIFICATES, "Group1-Alice",
                        GatewayTlsCertificates(
                            "id1",
                            listOf("one", "two"),
                        )
                    )
                )
            )
        }

        @Test
        fun `identityAdded will not republish certificates`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `identityAdded will not republish certificates in different order`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)

            publisher.identityAdded(
                identityInfo.copy(
                    tlsCertificates = identityInfo.tlsCertificates.reversed()
                )
            )

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }

        @Test
        fun `identityAdded will republish new certificates`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            publisher.identityAdded(identityInfo)
            val certificatesTwo = listOf("two", "three")

            publisher.identityAdded(
                identityInfo.copy(
                    tlsCertificates = certificatesTwo
                )
            )

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_CERTIFICATES, "Group1-Alice", GatewayTlsCertificates("id1", identityInfo.tlsCertificates))),
                listOf(Record(GATEWAY_TLS_CERTIFICATES, "Group1-Alice", GatewayTlsCertificates("id1", certificatesTwo))),
            )
        }

        @Test
        fun `publishIfNeeded will wait for certificates to be published`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())
            val future = mock<CompletableFuture<Unit>>()
            whenever(mockPublisher.constructed().first().publish(any())).doReturn(listOf(future))

            publisher.identityAdded(identityInfo)

            verify(future).join()
        }

        @Test
        fun `identityAdded will not publish before the subscription started`() {
            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will not publish before it has the snapshots`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will not publish before the publisher is ready`() {
            publisher.start()
            whenever(mockPublisher.constructed().first().isRunning).doReturn(false)
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(emptyMap())

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `identityAdded will publish after it has the snapshot`() {
            publisher.identityAdded(identityInfo)
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onSnapshot(emptyMap())
            processor.firstValue.onSnapshot(emptyMap())

            verify(mockPublisher.constructed().first(), times(1)).publish(any())
        }
    }

    @Nested
    inner class ProcessorTests {
        @Test
        fun `onSnapshot mark the publisher as ready`() {
            publisher.start()
            val future = creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(future.isDone).isTrue
        }

        @Test
        fun `onSnapshot save the data correctly`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onSnapshot(
                mapOf(
                    "Group1-Alice" to GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.tlsCertificates,
                    )
                )
            )

            publisher.identityAdded(identityInfo)
            verify(mockPublisher.constructed().first(), never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())
            processor.firstValue.onSnapshot(
                mapOf(
                    "Group1-Alice" to GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.tlsCertificates,
                    )
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", "Group1-Alice",
                    null
                ),
                null, emptyMap()
            )

            publisher.identityAdded(identityInfo)

            verify(mockPublisher.constructed().first()).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            publisher.start()
            creteResources.get().invoke(ResourcesHolder())

            processor.firstValue.onNext(
                Record(
                    "",
                    "Group1-Alice",
                    GatewayTlsCertificates(
                        identityInfo.tlsTenantId,
                        identityInfo.tlsCertificates,
                    )
                ),
                null, emptyMap()
            )

            publisher.identityAdded(identityInfo)
            verify(mockPublisher.constructed().first(), never()).publish(any())
        }
    }
    @Nested
    inner class CreateResourcesTests {
        @Test
        fun `createResources will not complete before the snapshot is ready`() {
            val future = creteResources.get().invoke(ResourcesHolder())

            assertThat(future.isDone).isFalse
        }
    }
}
