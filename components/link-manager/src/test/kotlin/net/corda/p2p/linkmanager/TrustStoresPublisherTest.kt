package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.domino.logic.DominoTile
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

    private val localIdentity = LinkManagerNetworkMap.HoldingIdentity(
        "local",
        "groupOne",
    )
    private val remoteIdentity = LinkManagerNetworkMap.HoldingIdentity(
        "remote",
        "groupTwo",
    )
    private val tile = mock<DominoTile> {
        on { name } doReturn LifecycleCoordinatorName("name")
    }
    private val linkManagerHostingMap = mock<LinkManagerHostingMap> {
        on { isHostedLocally(localIdentity) } doReturn true
        on { isHostedLocally(remoteIdentity) } doReturn false
        on { dominoTile } doReturn tile
    }

    private val certificates = listOf("one", "two")
    private val linkManagerNetworkMap = mock<LinkManagerNetworkMap> {
        on { getCertificates(localIdentity.groupId) } doReturn certificates
        on { dominoTile } doReturn tile
    }

    private val trustStoresPublisher = TrustStoresPublisher(
        subscriptionFactory,
        publisherFactory,
        lifecycleCoordinatorFactory,
        configuration,
        instanceId,
        linkManagerHostingMap,
        linkManagerNetworkMap,
    )

    @Nested
    inner class IdentityAddedTests {

        @Test
        fun `identityAdded will publish unpublished group`() {
            trustStoresPublisher.start()

            trustStoresPublisher.identityAdded(localIdentity)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, localIdentity.groupId, GatewayTruststore(certificates)))
            )
        }

        @Test
        fun `identityAdded will not republish certificates`() {
            trustStoresPublisher.start()
            trustStoresPublisher.identityAdded(localIdentity)

            trustStoresPublisher.identityAdded(localIdentity)

            verify(publisher, times(1)).publish(any())
        }

        @Test
        fun `identityAdded will not republish certificates in different order`() {
            trustStoresPublisher.start()
            trustStoresPublisher.identityAdded(localIdentity)
            whenever(linkManagerNetworkMap.getCertificates(localIdentity.groupId)).doReturn(certificates.reversed())

            trustStoresPublisher.identityAdded(localIdentity)

            verify(publisher, times(1)).publish(any())
        }

        @Test
        fun `identityAdded will ignore remote groups`() {
            trustStoresPublisher.start()

            trustStoresPublisher.identityAdded(remoteIdentity)

            verify(publisher, never()).publish(any())
        }

        @Test
        fun `identityAdded will ignore groups without certificates`() {
            val localIdentityWithoutCertificates = LinkManagerNetworkMap.HoldingIdentity(
                "name",
                "local as well"
            )
            whenever(linkManagerHostingMap.isHostedLocally(localIdentityWithoutCertificates)).doReturn(true)
            whenever(linkManagerNetworkMap.getCertificates(localIdentityWithoutCertificates.groupId)).doReturn(null)
            trustStoresPublisher.start()

            trustStoresPublisher.identityAdded(localIdentityWithoutCertificates)

            verify(publisher, never()).publish(any())
        }

        @Test
        fun `identityAdded will republish new certificates`() {
            trustStoresPublisher.start()
            trustStoresPublisher.identityAdded(localIdentity)
            val certificatesTwo = listOf("two", "three")
            whenever(linkManagerNetworkMap.getCertificates(localIdentity.groupId)) doReturn certificatesTwo

            trustStoresPublisher.identityAdded(localIdentity)

            assertThat(publishedRecords.allValues).containsExactly(
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, localIdentity.groupId, GatewayTruststore(certificates))),
                listOf(Record(GATEWAY_TLS_TRUSTSTORES, localIdentity.groupId, GatewayTruststore(certificatesTwo))),
            )
        }

        @Test
        fun `publishGroupIfNeeded will wait for certificates to be published`() {
            trustStoresPublisher.start()
            val future = mock<CompletableFuture<Unit>>()
            whenever(publisher.publish(any())).doReturn(listOf(future))

            trustStoresPublisher.identityAdded(localIdentity)

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

            processor.firstValue.onSnapshot(
                mapOf(
                    localIdentity.groupId to GatewayTruststore(
                        certificates
                    )
                )
            )

            trustStoresPublisher.identityAdded(localIdentity)

            verify(publisher, never()).publish(any())
        }

        @Test
        fun `onNext remove item from published stores`() {
            trustStoresPublisher.start()
            trustStoresPublisher.createResources(ResourcesHolder())
            processor.firstValue.onSnapshot(
                mapOf(
                    localIdentity.groupId to GatewayTruststore(
                        certificates
                    )
                )
            )

            processor.firstValue.onNext(
                Record(
                    "", localIdentity.groupId,
                    null
                ),
                null, emptyMap()
            )

            trustStoresPublisher.identityAdded(localIdentity)

            verify(publisher).publish(any())
        }

        @Test
        fun `onNext add item to published stores`() {
            trustStoresPublisher.start()
            trustStoresPublisher.createResources(ResourcesHolder())

            processor.firstValue.onNext(
                Record(
                    "", localIdentity.groupId,
                    GatewayTruststore(
                        certificates
                    )
                ),
                null, emptyMap()
            )

            trustStoresPublisher.identityAdded(localIdentity)
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
