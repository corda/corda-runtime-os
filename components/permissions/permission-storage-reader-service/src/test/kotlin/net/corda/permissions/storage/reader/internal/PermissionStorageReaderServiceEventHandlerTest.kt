package net.corda.permissions.storage.reader.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.DB_PASS
import net.corda.schema.configuration.ConfigKeys.DB_USER
import net.corda.schema.configuration.ConfigKeys.JDBC_URL
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.eq

class PermissionStorageReaderServiceEventHandlerTest {

    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val entityManagerFactoryFactory = mock<() -> EntityManagerFactory> {
        on { this.invoke() }.doReturn(entityManagerFactory)
    }
    private val pvCache = mock<PermissionValidationCache>()
    private val permissionValidationCacheService = mock<PermissionValidationCacheService>().apply {
        whenever(permissionValidationCache).thenReturn(pvCache)
    }
    private val pmCache = mock<PermissionManagementCache>()
    private val permissionManagementCacheService = mock<PermissionManagementCacheService>().apply {
        whenever(permissionManagementCache).thenReturn(pmCache)
    }
    private val permissionStorageReader = mock<PermissionStorageReader>()
    private val permissionStorageReaderFactory = mock<PermissionStorageReaderFactory>().apply {
        whenever(create(any(), any(), any(), any())).thenReturn(permissionStorageReader)
    }

    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(publisher)
    }
    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>().apply {
        whenever(followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )).thenReturn(registrationHandle)
    }

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionValidationCacheService,
        permissionManagementCacheService,
        permissionStorageReaderFactory,
        publisherFactory,
        mock(),
        entityManagerFactoryFactory,
    )

    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    private val reconciliationConfigMap = mapOf("$RECONCILIATION_CONFIG.$RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS" to 12345L)

    private val config = configFactory.create(
        ConfigFactory.empty()
            .withValue(
                DB_CONFIG,
                ConfigValueFactory.fromMap(mapOf(JDBC_URL to "dbUrl", DB_USER to "dbUser", DB_PASS to "dbPass"))
            )
    ).withFallback(ConfigFactory.parseMap(reconciliationConfigMap))

    private val bootstrapConfig =
        mapOf(BOOT_CONFIG to config, MESSAGING_CONFIG to configFactory.create(ConfigFactory.empty()))

    @Test
    fun `processing a START event follows and starts dependencies`() {

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registrationHandle)
        verify(permissionManagementCacheService).start()
        verify(permissionValidationCacheService).start()
    }

    @Test
    fun `processing an UP event when the service is started starts the storage reader`() {
        assertNull(handler.crsSub)

        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        verify(permissionValidationCacheService).start()
        verify(permissionManagementCacheService).start()
    }

    @Test
    fun `processing config change event starts the storage reader`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)

        assertNull(handler.permissionStorageReader)

        handler.onConfigurationUpdated(bootstrapConfig)

        assertNotNull(handler.permissionStorageReader)
        assertEquals(12345L, handler.reconciliationTaskIntervalMs)
        verify(permissionStorageReader).start()
    }

    @Test
    fun `processing an UP event when the service is started updates the service's status to UP`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(ConfigChangedEvent(bootstrapConfig.keys, bootstrapConfig), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN event when the service is started stops the storage reader`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.onConfigurationUpdated(bootstrapConfig)

        assertNotNull(handler.permissionStorageReader)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        assertNull(handler.permissionStorageReader)
        verify(permissionStorageReader).close()
    }

    @Test
    fun `processing a DOWN event when the service is started updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.onConfigurationUpdated(bootstrapConfig)

        assertNotNull(handler.permissionStorageReader)
        assertNotNull(handler.publisher)

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.permissionStorageReader)
        assertNull(handler.publisher)

        verify(permissionManagementCacheService).stop()
        verify(permissionValidationCacheService).stop()
        verify(registrationHandle).close()
        verify(permissionStorageReader).close()
        verify(publisher).close()
    }

    @Test
    fun `processing a stop event updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(StopEvent(), coordinator)
    }

    @Test
    fun `processing an onConfigurationUpdated event creates publisher and permission storage reader`() {
        whenever(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)
        whenever(permissionStorageReaderFactory.create(eq(pvCache), eq(pmCache), eq(publisher), any()))
            .thenReturn(permissionStorageReader)

        handler.processEvent(
            ConfigChangedEvent(setOf(BOOT_CONFIG, MESSAGING_CONFIG), bootstrapConfig),
            coordinator
        )

        verify(publisher).start()
        verify(permissionStorageReader).start()
        assertNotNull(handler.reconciliationTaskIntervalMs)
    }

    @Test
    fun `processing a ReconcilePermissionSummaryEvent executes reconciliation task and schedules next run`() {
        handler.reconciliationTaskIntervalMs = 11111L
        handler.permissionStorageReader = permissionStorageReader

        handler.processEvent(ReconcilePermissionSummaryEvent("PermissionStorageReaderServiceEventHandler"), coordinator)

        verify(permissionStorageReader).reconcilePermissionSummaries()
        verify(coordinator).setTimer(eq("PermissionStorageReaderServiceEventHandler"), eq(11111L), any())
    }

}