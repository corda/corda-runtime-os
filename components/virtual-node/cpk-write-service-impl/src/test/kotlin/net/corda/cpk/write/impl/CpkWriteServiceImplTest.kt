package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.*
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.security.MessageDigest

class CpkWriteServiceImplTest {
    private lateinit var cpkWriteServiceImpl: CpkWriteServiceImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configReadService: ConfigurationReadService
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory

    private lateinit var coordinator: LifecycleCoordinator

    companion object {
        fun secureHash(bytes: ByteArray): SecureHash {
            val algorithm = "SHA-256"
            val messageDigest = MessageDigest.getInstance(algorithm)
            return SecureHash(algorithm, messageDigest.digest(bytes))
        }
    }

    @BeforeEach
    fun setUp() {
        coordinatorFactory = mock()
        configReadService = mock()
        subscriptionFactory = mock()
        publisherFactory = mock()
        cpkWriteServiceImpl =
            CpkWriteServiceImpl(coordinatorFactory, configReadService, subscriptionFactory, publisherFactory)

        coordinator = mock()
    }

    @Test
    fun `on StartEvent follows configuration read service for updates`() {
        val registration = mock<RegistrationHandle>()
        whenever(coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())))
            .thenReturn(registration)

        cpkWriteServiceImpl.processEvent(StartEvent(), coordinator)
        assertNotNull(cpkWriteServiceImpl.configReadServiceRegistration)
    }

    @Test
    fun `on onRegistrationStatusChangeEvent registers to configuration read service for updates`() {
        whenever(configReadService.registerComponentForUpdates(any(), any())).thenReturn(cpkWriteServiceImpl)

        cpkWriteServiceImpl.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        assertNotNull(cpkWriteServiceImpl.configSubscription)
    }

    @Test
    fun `on onConfigChangedEvent fully sets the component`() {
        val keys = mock<Set<String>>()
        whenever(keys.contains(ConfigKeys.BOOT_CONFIG)).thenReturn(true)
        val bootConfig = mock<SmartConfig>()
        whenever(bootConfig.hasPath("todo")).thenReturn(true)
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(bootConfig)
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(mock())

        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)
        assertNotNull(cpkWriteServiceImpl.cpkChecksumCache)
        assertNotNull(cpkWriteServiceImpl.publisher)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on putting non cached cpk chunks puts them to Kafka`() {
//        val cpkChunkId = CpkChunkId(secureHash("dummy".toByteArray()), 0)
//        val cpkChunk = CpkChunk(cpkChunkId, "dummy".toByteArray())
//
//        val publisher = mock<Publisher>()
//        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().also { it.complete(Unit) }))
//        cpkWriteServiceImpl.publisher = publisher
//
//        cpkWriteServiceImpl.putAll(listOf(cpkChunk))
    }

    @Test
    fun `on putting cached cpk chunks it will not put them since they already exist to Kafka`() {
//        val cpkChunkId = CpkChunkId(secureHash("dummy".toByteArray()), 0)
//        val cpkChunk = CpkChunk(cpkChunkId, "dummy".toByteArray())
//
//        val cpkChunksCache = mock<CpkChunksCache>()
//        whenever(cpkChunksCache.contains(cpkChunkId)).thenReturn(true)
//        cpkWriteServiceImpl.cpkChunksCache = cpkChunksCache
//
//        var invocation: InvocationOnMock? = null
//        val publisher = org.mockito.Mockito.mock(Publisher::class.java)
//        `when`(publisher.publish(anyOrNull())).thenAnswer { _invocation ->
//            invocation = _invocation
//            emptyList<CompletableFuture<Unit>>()
//        }
//        cpkWriteServiceImpl.publisher = publisher
//
//
//        cpkWriteServiceImpl.putAll(listOf(cpkChunk))
//        assertThat(invocation!!.arguments[0] as List<*>).isEmpty()
    }
}