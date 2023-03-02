package net.corda.cpk.write.impl

import net.corda.chunking.Constants.Companion.APP_LEVEL_CHUNK_MESSAGE_OVERHEAD
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CPK_WRITE_INTERVAL_MS
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
        coordinator = mock()
        coordinatorFactory = mock<LifecycleCoordinatorFactory>()
            .apply { `when`(createCoordinator(any(), any())).thenReturn(coordinator) }
        configReadService = mock()
        subscriptionFactory = mock()
        publisherFactory = mock()
        dbConnectionManager = mock()
        cpkWriteServiceImpl =
            CpkWriteServiceImpl(coordinatorFactory, configReadService, subscriptionFactory, publisherFactory, dbConnectionManager)

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
        verify(coordinator).createManagedResource(
            argThat { equals(CpkWriteServiceImpl.REGISTRATION) },
            any<() -> RegistrationHandle>()
        )
    }

    @Test
    fun `on onRegistrationStatusChangeEvent registers to configuration read service for updates`() {
        whenever(configReadService.registerComponentForUpdates(any(), any())).thenReturn(mock())

        cpkWriteServiceImpl.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        verify(coordinator).createManagedResource(
            argThat { equals(CpkWriteServiceImpl.Companion.CONFIG_HANDLE) },
            any<() -> RegistrationHandle>()
        )
    }

    @Test
    fun `on ConfigChangedEvent fully sets the component`() {
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(mock())
        whenever(subscriptionFactory.createCompactedSubscription<Any, Any>(any(), any(), any())).thenReturn(mock())
        whenever(dbConnectionManager.getClusterEntityManagerFactory()).thenReturn(mock())

        val keys = mock<Set<String>>()
        val config = mock<Map<String, SmartConfig>>()
        val messagingSmartConfigMock = mock<SmartConfig>()
        val mockReconciliationConfig = mock<SmartConfig>()

        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(messagingSmartConfigMock)
        whenever(config[ConfigKeys.RECONCILIATION_CONFIG]).thenReturn(mockReconciliationConfig)
        whenever(mockReconciliationConfig.getLong(RECONCILIATION_CPK_WRITE_INTERVAL_MS)).thenReturn(10000L)

        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNotNull(cpkWriteServiceImpl.timeout)
        assertNotNull(cpkWriteServiceImpl.timerEventIntervalMs)
        assertNotNull(cpkWriteServiceImpl.cpkChecksumsCache)
        assertNotNull(cpkWriteServiceImpl.cpkChunksPublisher)
        assertNotNull(cpkWriteServiceImpl.cpkStorage)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `on ConfigChangedEvent with reconciliation config overwrites the config set in bootstrap`() {
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(mock())
        whenever(subscriptionFactory.createCompactedSubscription<Any, Any>(any(), any(), any())).thenReturn(mock())
        whenever(dbConnectionManager.getClusterEntityManagerFactory()).thenReturn(mock())

        val keys = mock<Set<String>>()
        val config = mock<Map<String, SmartConfig>>()
        val messagingSmartConfigMock = mock<SmartConfig>()
        val mockReconciliationConfig = mock<SmartConfig>()
        val mockConfig = mock<SmartConfig>()
        val extraReconciliationConfig = mock<SmartConfig>()
        val totalConfig = mock<SmartConfig>()

        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(messagingSmartConfigMock)
        whenever(config[ConfigKeys.RECONCILIATION_CONFIG]).thenReturn(extraReconciliationConfig)
        whenever(extraReconciliationConfig.withFallback(mockConfig)).thenReturn(totalConfig)
        whenever(totalConfig.getConfig(ConfigKeys.RECONCILIATION_CONFIG)).thenReturn(mockReconciliationConfig)
        whenever(mockReconciliationConfig.getLong(RECONCILIATION_CPK_WRITE_INTERVAL_MS)).thenReturn(10000L)

        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNotNull(cpkWriteServiceImpl.timeout)
        assertNotNull(cpkWriteServiceImpl.timerEventIntervalMs)
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
        whenever(cpkStorage.getAllCpkFileIds(emptyList())).thenReturn(listOf(cpkChecksum))
        whenever(cpkStorage.getCpkFileById(cpkChecksum)).thenReturn(CpkFile(cpkChecksum, cpkData))

        val configChangedEvent = ConfigChangedEvent(
            setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RECONCILIATION_CONFIG),
            mapOf(
                ConfigKeys.MESSAGING_CONFIG to mock() {
                    on { getInt(MessagingConfig.MAX_ALLOWED_MSG_SIZE) }.doReturn(APP_LEVEL_CHUNK_MESSAGE_OVERHEAD + 32)
                },
                ConfigKeys.RECONCILIATION_CONFIG to mock() {
                    on { getLong(RECONCILIATION_CPK_WRITE_INTERVAL_MS) }.doReturn(1)
                }
            )
        )
        cpkWriteServiceImpl.processEvent(configChangedEvent, mock())

        cpkWriteServiceImpl.cpkStorage = cpkStorage

        val chunks = mutableListOf<Chunk>()
        val cpkChunksPublisher = org.mockito.Mockito.mock(CpkChunksPublisher::class.java)
        org.mockito.Mockito.`when`(cpkChunksPublisher.put(anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            chunks.add(invocation.arguments[1] as Chunk)
        }
        cpkWriteServiceImpl.cpkChunksPublisher = cpkChunksPublisher

        cpkWriteServiceImpl.putMissingCpk()

        // assert that we can publish multiple CPKs
        cpkWriteServiceImpl.putMissingCpk()

        assertTrue(chunks.size == 4)
        assertTrue(chunks[0].data.equals(ByteBuffer.wrap(cpkData)))
        assertTrue(chunks[1].data.limit() == 0)
        assertTrue(chunks[2].data.equals(ByteBuffer.wrap(cpkData)))
        assertTrue(chunks[3].data.limit() == 0)

        assertEquals("${cpkChecksum.toHexString()}.cpk", chunks[0].fileName)
        assertEquals("${cpkChecksum.toHexString()}.cpk", chunks[2].fileName)
    }

    @Test
    fun `on failing at sub services creation closes the component`() {
        whenever(publisherFactory.createPublisher(any(), any())).thenThrow(CordaRuntimeException(""))

        val keys = mock<Set<String>>()
        val config = mock<Map<String, SmartConfig>>()
        val mockReconciliationConfig = mock<SmartConfig>()
        val messagingSmartConfigMock = mock<SmartConfig>()

        whenever(config[ConfigKeys.BOOT_CONFIG]).thenReturn(mock())
        whenever(config[ConfigKeys.MESSAGING_CONFIG]).thenReturn(messagingSmartConfigMock)
        whenever(config[ConfigKeys.RECONCILIATION_CONFIG]).thenReturn(mockReconciliationConfig)
        whenever(mockReconciliationConfig.getLong(RECONCILIATION_CPK_WRITE_INTERVAL_MS)).thenReturn(10000L)

        cpkWriteServiceImpl.processEvent(ConfigChangedEvent(keys, config), coordinator)

        assertNull(coordinator.getManagedResource(CpkWriteServiceImpl.Companion.REGISTRATION))
        assertNull(coordinator.getManagedResource(CpkWriteServiceImpl.Companion.CONFIG_HANDLE))
        assertNull(cpkWriteServiceImpl.cpkChecksumsCache)
        assertNull(cpkWriteServiceImpl.cpkChunksPublisher)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `on StopEvent cancels timer`() {
        cpkWriteServiceImpl.processEvent(StopEvent(), coordinator)
        verify(coordinator).cancelTimer(CpkWriteServiceImpl::class.simpleName!!)
    }
}