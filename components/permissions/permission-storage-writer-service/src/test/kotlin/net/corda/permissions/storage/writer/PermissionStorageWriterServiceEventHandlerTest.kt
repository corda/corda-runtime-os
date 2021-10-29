package net.corda.permissions.storage.writer

import net.corda.libs.permissions.storage.writer.PermissionStorageWriter
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionStorageWriterServiceEventHandlerTest {

    private val permissionStorageWriter = mock<PermissionStorageWriter>()
    private val permissionStorageWriterFactory = mock<PermissionStorageWriterFactory>().apply {
        whenever(create(any())).thenReturn(permissionStorageWriter)
    }
    private val handler = PermissionStorageWriterServiceEventHandler(mock(), permissionStorageWriterFactory)

    @Test
    fun `processing a start event starts the permission storage writer`() {
        assertNull(handler.permissionStorageWriter)
        handler.processEvent(StartEvent(), mock())
        assertNotNull(handler.permissionStorageWriter)
        verify(permissionStorageWriter).start()
    }

    @Test
    fun `processing a stop event stops the permission storage writer`() {
        handler.processEvent(StartEvent(), mock())
        val writer = handler.permissionStorageWriter
        assertNotNull(writer)
        handler.processEvent(StopEvent(), mock())
        assertNull(handler.permissionStorageWriter)
        verify(permissionStorageWriter).stop()
    }
}