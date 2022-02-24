package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.write.impl.services.db.CpkChecksumData
import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.*
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import java.nio.ByteBuffer
import java.security.MessageDigest

class CpkWriteServiceImplTest {
    private lateinit var cpkWriteServiceImpl: CpkWriteServiceImpl
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configReadService: ConfigurationReadService
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var dbConnectionManager: DbConnectionManager

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
        dbConnectionManager = mock()
        cpkWriteServiceImpl =
            CpkWriteServiceImpl(coordinatorFactory, configReadService, subscriptionFactory, publisherFactory, dbConnectionManager)

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
        assertNotNull(cpkWriteServiceImpl.cpkChecksumsCache)
        assertNotNull(cpkWriteServiceImpl.cpkChunksPublisher)
        assertNotNull(cpkWriteServiceImpl.cpkStorage)
        assertNotNull(cpkWriteServiceImpl.timeout)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on putMissingCpk chunks the CPK and publishes it`() {
        val cpkData = byteArrayOf(0x01, 0x02, 0x03)
        val cpkChecksum = secureHash(cpkData)
        val cpkStorage = mock<CpkStorage>()
        whenever(cpkStorage.getCpkIdsNotIn(emptySet())).thenReturn(setOf(cpkChecksum))
        whenever(cpkStorage.getCpkDataByCpkId(cpkChecksum)).thenReturn(CpkChecksumData(cpkChecksum, cpkData))
        cpkWriteServiceImpl.cpkStorage = cpkStorage

        val chunks = mutableListOf<Chunk>()
        val cpkChunksPublisher = mock(CpkChunksPublisher::class.java)
        `when`(cpkChunksPublisher.put(anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            chunks.add(invocation.arguments[1] as Chunk)
        }
        cpkWriteServiceImpl.cpkChunksPublisher = cpkChunksPublisher

        cpkWriteServiceImpl.putMissingCpk()
        assertTrue(chunks.size == 2)
        assertTrue(chunks[0].data.equals(ByteBuffer.wrap(cpkData)))
    }

//    @Test
//    fun `on putting non cached cpk chunks puts them to Kafka`() {
//        val cpkChunkId = CpkChunkId(secureHash("dummy".toByteArray()), 0)
//        val cpkChunk = CpkChunk(cpkChunkId, "dummy".toByteArray())
//
//        val publisher = mock<Publisher>()
//        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().also { it.complete(Unit) }))
//        cpkWriteServiceImpl.publisher = publisher
//
//        cpkWriteServiceImpl.putAll(listOf(cpkChunk))
//    }

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