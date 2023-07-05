package net.corda.interop.identity.cache.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.interop.identity.cache.InteropIdentityCacheEntry
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


class InteropIdentityCacheServiceImplTest {
    @Test
    fun `get before put returns empty map`() {
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

        val cache = InteropIdentityCacheServiceImpl(coordinatorFactory, mock(), subscriptionFactory)

        val shortHash = "1234567890"
        val response = cache.getInteropIdentities(shortHash)

        assertThat(response).isInstanceOf(Set::class.java)
        assertThat(response.isEmpty()).isTrue
    }

    @Test
    fun `get after put returns value from put`() {
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

        val cache = InteropIdentityCacheServiceImpl(coordinatorFactory, mock(), subscriptionFactory)

        val shortHash = "1234567890"
        val interopIdentity = InteropIdentityCacheEntry(
            groupId = UUID.randomUUID().toString(),
            x500Name = "X500 name #1",
            holdingIdentityShortHash = shortHash
        )

        cache.putInteropIdentity(shortHash, interopIdentity)

        val interopIdentities = cache.getInteropIdentities(shortHash)

        assertThat(interopIdentities).isInstanceOf(Set::class.java)
        assertThat(interopIdentities.size).isEqualTo(1)

        val value = interopIdentities.single()

        assertThat(value).isEqualTo(interopIdentity)
    }

    @Test
    fun `get returns values from multiple puts as a map`() {
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

        val cache = InteropIdentityCacheServiceImpl(coordinatorFactory, mock(), subscriptionFactory)

        val shortHash = "1234567890"

        val interopIdentity1 = InteropIdentityCacheEntry(
            groupId = UUID.randomUUID().toString(),
            x500Name = "X500 name #1",
            holdingIdentityShortHash = shortHash
        )

        val interopIdentity2 = InteropIdentityCacheEntry(
            groupId = UUID.randomUUID().toString(),
            x500Name = "X500 name #2",
            holdingIdentityShortHash = shortHash
        )

        cache.putInteropIdentity(shortHash, interopIdentity1)
        cache.putInteropIdentity(shortHash, interopIdentity2)

        val interopIdentities = cache.getInteropIdentities(shortHash)

        assertThat(interopIdentities).isInstanceOf(Set::class.java)
        assertThat(interopIdentities.size).isEqualTo(2)

        assertThat(interopIdentities).contains(interopIdentity1, interopIdentity2)
    }
}
