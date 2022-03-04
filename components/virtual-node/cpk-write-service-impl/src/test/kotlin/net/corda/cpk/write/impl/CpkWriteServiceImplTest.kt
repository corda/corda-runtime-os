package net.corda.cpk.write.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.write.impl.services.db.CpkChecksumToData
import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
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
        whenever(
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                    LifecycleCoordinatorName.forComponent<DbConnectionManager>()
                )
            )
        )
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
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(mock())
        whenever(subscriptionFactory.createCompactedSubscription<Any, Any>(any(), any(), any())).thenReturn(mock())
        whenever(dbConnectionManager.getClusterEntityManagerFactory()).thenReturn(mock())

        val keys = mock<Set<String>>()
        whenever(keys.contains(ConfigKeys.BOOT_CONFIG)).thenReturn(true)
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        val messagingSmartConfigMock = mock<SmartConfig>()
        whenever(messagingSmartConfigMock.withFallback(any())).thenReturn(mock())
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(messagingSmartConfigMock)
        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNotNull(cpkWriteServiceImpl.timeout)
        assertNotNull(cpkWriteServiceImpl.timerEventInterval)
        assertNotNull(cpkWriteServiceImpl.cpkChecksumsCache)
        assertNotNull(cpkWriteServiceImpl.cpkChunksPublisher)
        assertNotNull(cpkWriteServiceImpl.cpkStorage)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on putMissingCpk chunks the CPK and publishes it`() {
        val cpkData = byteArrayOf(0x01, 0x02, 0x03)
        val cpkChecksum = secureHash(cpkData)
        val cpkStorage = mock<CpkStorage>()
        whenever(cpkStorage.getCpkIdsNotIn(emptyList())).thenReturn(listOf(cpkChecksum))
        whenever(cpkStorage.getCpkDataByCpkId(cpkChecksum)).thenReturn(CpkChecksumToData(cpkChecksum, cpkData))
        cpkWriteServiceImpl.cpkStorage = cpkStorage

        val chunks = mutableListOf<Chunk>()
        val cpkChunksPublisher = org.mockito.Mockito.mock(CpkChunksPublisher::class.java)
        org.mockito.Mockito.`when`(cpkChunksPublisher.put(anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            chunks.add(invocation.arguments[1] as Chunk)
        }
        cpkWriteServiceImpl.cpkChunksPublisher = cpkChunksPublisher

        cpkWriteServiceImpl.putMissingCpk()
        assertTrue(chunks.size == 2)
        assertTrue(chunks[0].data.equals(ByteBuffer.wrap(cpkData)))
        assertEquals("${cpkChecksum.toHexString()}.cpk", chunks[0].fileName)
    }

    @Test
    fun `on failing at sub services creation closes the component`() {
        whenever(publisherFactory.createPublisher(any(), any())).thenThrow(CordaRuntimeException(""))

        val keys = mock<Set<String>>()
        whenever(keys.contains(ConfigKeys.BOOT_CONFIG)).thenReturn(true)
        val config = mock<Map<String, SmartConfig>>()
        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(mock())
        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNull(cpkWriteServiceImpl.configReadServiceRegistration)
        assertNull(cpkWriteServiceImpl.configSubscription)
        assertNull(cpkWriteServiceImpl.cpkChecksumsCache)
        assertNull(cpkWriteServiceImpl.cpkChunksPublisher)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}