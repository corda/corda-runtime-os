package net.corda.interop.identity.cache.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.data.interop.InteropIdentity
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
import kotlin.collections.HashMap


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

        assertThat(response).isInstanceOf(HashMap::class.java)
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
        val interopIdentity = InteropIdentity().apply {
            groupId = UUID.randomUUID().toString()
            x500Name = "X500 name #1"
            hostingVnode = "Hosting VNode"
        }

        cache.putInteropIdentities(shortHash, interopIdentity)

        val interopIdentities = cache.getInteropIdentities(shortHash)

        assertThat(interopIdentities).isInstanceOf(HashMap::class.java)
        assertThat(interopIdentities.size).isEqualTo(1)

        val key = interopIdentities.keys.single()

        assertThat(key).isEqualTo(interopIdentity.groupId)

        val value = interopIdentities[key]

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

        val interopIdentity1 = InteropIdentity().apply {
            groupId = UUID.randomUUID().toString()
            x500Name = "X500 name #1"
            hostingVnode = "Hosting VNode"
        }

        val interopIdentity2 = InteropIdentity().apply {
            groupId = UUID.randomUUID().toString()
            x500Name = "X500 name #2"
            hostingVnode = "Hosting VNode"
        }

        cache.putInteropIdentities(shortHash, interopIdentity1)
        cache.putInteropIdentities(shortHash, interopIdentity2)

        val interopIdentities = cache.getInteropIdentities(shortHash)

        assertThat(interopIdentities).isInstanceOf(HashMap::class.java)
        assertThat(interopIdentities.size).isEqualTo(2)

        val keys = interopIdentities.keys

        assertThat(keys).contains(interopIdentity1.groupId, interopIdentity2.groupId)

        val value1 = interopIdentities[interopIdentity1.groupId]
        val value2 = interopIdentities[interopIdentity2.groupId]

        assertThat(value1).isEqualTo(interopIdentity1)
        assertThat(value2).isEqualTo(interopIdentity2)
    }
}
