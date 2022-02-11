package net.corda.permissions.storage.reader.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.cache.PermissionCache
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
import net.corda.permissions.cache.PermissionCacheService
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.DB_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.DB_PASS
import net.corda.schema.configuration.ConfigKeys.Companion.DB_USER
import net.corda.schema.configuration.ConfigKeys.Companion.JDBC_URL
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

class PermissionStorageReaderServiceEventHandlerTest {

    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val entityManagerFactoryFactory = mock<() -> EntityManagerFactory> {
        on { this.invoke() }.doReturn(entityManagerFactory)
    }
    private val permissionCache = mock<PermissionCache>()
    private val permissionCacheService = mock<PermissionCacheService>().apply {
        whenever(permissionCache).thenReturn(this@PermissionStorageReaderServiceEventHandlerTest.permissionCache)
    }
    private val permissionStorageReader = mock<PermissionStorageReader>()
    private val permissionStorageReaderFactory = mock<PermissionStorageReaderFactory>().apply {
        whenever(create(any(), any(), any())).thenReturn(permissionStorageReader)
    }

    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory>().apply {
        whenever(createPublisher(any(), any())).thenReturn(publisher)
    }
    private val registrationHandle = mock<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator>().apply {
        whenever(followStatusChangesByName(any())).thenReturn(registrationHandle)
    }

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionCacheService,
        permissionStorageReaderFactory,
        publisherFactory,
        mock(),
        entityManagerFactoryFactory,
    )

    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
    private val config = configFactory.create(
        ConfigFactory.empty()
            .withValue(
                DB_CONFIG,
                ConfigValueFactory.fromMap(mapOf(JDBC_URL to "dbUrl", DB_USER to "dbUser", DB_PASS to "dbPass"))
            )
    )

    private val bootstrapConfig = mapOf(BOOT_CONFIG to config)

    @Test
    fun `processing a start event causes the service to follow permission cache status changes`() {
        assertNull(handler.registrationHandle)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.registrationHandle)
    }

    @Test
    fun `processing an UP event from the permission cache when the service is started starts the storage reader`() {
        assertNull(handler.permissionStorageReader)

        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.onConfigurationUpdated(setOf(BOOT_CONFIG), bootstrapConfig)

        assertNotNull(handler.permissionStorageReader)
        verify(permissionStorageReader).start()
    }

    @Test
    fun `processing an UP event from the permission cache when the service is started updates the service's status to UP`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `processing a DOWN event from the permission cache when the service is started stops the storage reader`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.onConfigurationUpdated(setOf(BOOT_CONFIG), bootstrapConfig)

        assertNotNull(handler.permissionStorageReader)

        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)

        assertNull(handler.permissionStorageReader)
        verify(permissionStorageReader).stop()
    }

    @Test
    fun `processing a DOWN event from the permission cache when the service is started updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }

    @Test
    fun `processing a stop event stops the service's dependencies`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        handler.onConfigurationUpdated(setOf(BOOT_CONFIG), bootstrapConfig)

        assertNotNull(handler.registrationHandle)
        assertNotNull(handler.permissionStorageReader)
        assertNotNull(handler.publisher)

        handler.processEvent(StopEvent(), coordinator)

        assertNull(handler.registrationHandle)
        assertNull(handler.permissionStorageReader)
        assertNull(handler.publisher)

        verify(registrationHandle).close()
        verify(permissionStorageReader).stop()
        verify(publisher).close()
    }

    @Test
    fun `processing a stop event updates the service's status to DOWN`() {
        handler.processEvent(StartEvent(), coordinator)
        handler.processEvent(StopEvent(), coordinator)
        verify(coordinator).updateStatus(LifecycleStatus.DOWN)
    }
}