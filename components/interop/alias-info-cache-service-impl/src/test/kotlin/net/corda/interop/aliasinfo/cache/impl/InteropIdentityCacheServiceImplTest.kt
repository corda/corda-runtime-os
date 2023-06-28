package net.corda.interop.aliasinfo.cache.impl

import net.corda.data.chunking.UploadStatus
import net.corda.data.chunking.UploadStatusKey
import net.corda.data.interop.InteropAliasIdentity
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
        val response = cache.getAliasIdentities(shortHash)

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
        val aliasIdentity = InteropAliasIdentity().apply {
            groupId = UUID.randomUUID().toString()
            aliasX500Name = "X500 name #1"
            hostingVnode = "Hosting VNode"
        }

        cache.putAliasIdentity(shortHash, aliasIdentity)

        val aliasIdentities = cache.getAliasIdentities(shortHash)

        assertThat(aliasIdentities).isInstanceOf(HashMap::class.java)
        assertThat(aliasIdentities.size).isEqualTo(1)

        val key = aliasIdentities.keys.single()

        assertThat(key).isEqualTo(aliasIdentity.groupId)

        val value = aliasIdentities[key]

        assertThat(value).isEqualTo(aliasIdentity)
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

        val aliasIdentity1 = InteropAliasIdentity().apply {
            groupId = UUID.randomUUID().toString()
            aliasX500Name = "X500 name #1"
            hostingVnode = "Hosting VNode"
        }

        val aliasIdentity2 = InteropAliasIdentity().apply {
            groupId = UUID.randomUUID().toString()
            aliasX500Name = "X500 name #2"
            hostingVnode = "Hosting VNode"
        }

        cache.putAliasIdentity(shortHash, aliasIdentity1)
        cache.putAliasIdentity(shortHash, aliasIdentity2)

        val aliasIdentities = cache.getAliasIdentities(shortHash)

        assertThat(aliasIdentities).isInstanceOf(HashMap::class.java)
        assertThat(aliasIdentities.size).isEqualTo(2)

        val keys = aliasIdentities.keys

        assertThat(keys).contains(aliasIdentity1.groupId, aliasIdentity2.groupId)

        val value1 = aliasIdentities[aliasIdentity1.groupId]
        val value2 = aliasIdentities[aliasIdentity2.groupId]

        assertThat(value1).isEqualTo(aliasIdentity1)
        assertThat(value2).isEqualTo(aliasIdentity2)
    }
}
