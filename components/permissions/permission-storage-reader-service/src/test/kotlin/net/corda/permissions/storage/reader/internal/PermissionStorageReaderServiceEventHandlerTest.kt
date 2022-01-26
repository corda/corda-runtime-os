package net.corda.permissions.storage.reader.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.common.ConfigKeys.BOOTSTRAP_CONFIG
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_CONFIG_KEY
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_PASSWORD
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_URL
import net.corda.libs.permissions.storage.common.ConfigKeys.DB_USER
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
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.cache.PermissionCacheService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

class PermissionStorageReaderServiceEventHandlerTest {

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

    private val allEntitiesSets = listOf(mock<JpaEntitiesSet>().apply {
        whenever(persistenceUnitName).thenReturn(DbSchema.RPC_RBAC)
    })

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionCacheService,
        permissionStorageReaderFactory,
        publisherFactory,
        mock(),
        mock(),
        allEntitiesSets,
        entityManagerFactoryCreationFn = ::testObtainEntityManagerFactory
    )

    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
    private val config = configFactory.create(
        ConfigFactory.empty()
            .withValue(
                DB_CONFIG_KEY,
                ConfigValueFactory.fromMap(mapOf(DB_URL to "dbUrl", DB_USER to "dbUser", DB_PASSWORD to "dbPass"))
            )
    )

    private val bootstrapConfig = mapOf(BOOTSTRAP_CONFIG to config)

    private fun testObtainEntityManagerFactory(
        dbConfig: SmartConfig, entityManagerFactoryFactory: EntityManagerFactoryFactory,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        Triple(
            dbConfig,
            entityManagerFactoryFactory,
            entitiesSet
        )
        return mock()
    }

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
        handler.onConfigurationUpdated(setOf(BOOTSTRAP_CONFIG), bootstrapConfig)

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
        handler.onConfigurationUpdated(setOf(BOOTSTRAP_CONFIG), bootstrapConfig)

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
        handler.onConfigurationUpdated(setOf(BOOTSTRAP_CONFIG), bootstrapConfig)

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