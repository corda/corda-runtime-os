package net.corda.interop.identity.registry.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.v5.application.interop.facade.FacadeId


class InteropIdentityRegistryServiceImplTest {
    @Test
    fun `get before put returns empty cache view`() {
        val coordinator = mock<LifecycleCoordinator>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).doReturn(coordinator)
        }

        val subscription: CompactedSubscription<UploadStatusKey, UploadStatus> = mock()
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            Mockito.`when`(createCompactedSubscription<UploadStatusKey, UploadStatus>(any(), any(), any())).thenReturn(
                subscription
            )
        }

        val cache = InteropIdentityRegistryServiceImpl(coordinatorFactory, mock(), subscriptionFactory)

        val shortHash = ShortHash.parse("123456789012")
        val response = cache.getVirtualNodeRegistryView(shortHash)

        assertThat(response.getIdentities().isEmpty()).isTrue
    }

    @Test
    fun `holding identity views are separate`() {
        val coordinator = mock<LifecycleCoordinator>()
        val coordinatorFactory = mock<LifecycleCoordinatorFactory>().also {
            whenever(it.createCoordinator(any(), any())).doReturn(coordinator)
        }

        val subscription: CompactedSubscription<UploadStatusKey, UploadStatus> = mock()
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            Mockito.`when`(createCompactedSubscription<UploadStatusKey, UploadStatus>(any(), any(), any())).thenReturn(
                subscription
            )
        }

        val cache = InteropIdentityRegistryServiceImpl(coordinatorFactory, mock(), subscriptionFactory)

        val shortHash1 = ShortHash.parse("123456789012")
        val shortHash2 = ShortHash.parse("210987654321")

        val view1 = cache.getVirtualNodeRegistryView(shortHash1)
        val view2 = cache.getVirtualNodeRegistryView(shortHash2)

        val interopIdentity1 = InteropIdentity(
            groupId = UUID.randomUUID(),
            x500Name = "C=GB, L=London, O=Alice",
            owningVirtualNodeShortHash = shortHash1,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        val interopIdentity2 = InteropIdentity(
            groupId = UUID.randomUUID(),
            x500Name = "C=GB, L=London, O=Bob",
            owningVirtualNodeShortHash = shortHash2,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://bob.corda5.r3.com:10000",
            enabled = true
        )

        cache.putInteropIdentity(shortHash1, interopIdentity1)
        cache.putInteropIdentity(shortHash2, interopIdentity2)

        assertThat(view1).isNotEqualTo(view2)

        assertThat(view1.getIdentities()).hasSize(1)
        assertThat(view1.getIdentities()).contains(interopIdentity1)

        assertThat(view2.getIdentities()).hasSize(1)
        assertThat(view2.getIdentities()).contains(interopIdentity2)
    }
}
