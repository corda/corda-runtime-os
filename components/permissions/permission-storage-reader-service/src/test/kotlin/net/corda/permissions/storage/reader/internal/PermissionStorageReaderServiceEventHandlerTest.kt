package net.corda.permissions.storage.reader.internal

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionCacheService,
        permissionStorageReaderFactory,
        mock(),
        publisherFactory,
        mock()
    )

    @Test
    fun `processing a start event starts the publisher`() {
        assertNull(handler.publisher)

        handler.processEvent(StartEvent(), coordinator)

        assertNotNull(handler.publisher)

        verify(publisher).start()
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

        assertNotNull(handler.permissionStorageReader)
        verify(permissionStorageReader).start()
    }

    @Test
    fun `processing an UP event from the permission cache when the service is not started throws an exception`() {
        assertThrows<IllegalStateException> {
            handler.processEvent(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), coordinator)
        }
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