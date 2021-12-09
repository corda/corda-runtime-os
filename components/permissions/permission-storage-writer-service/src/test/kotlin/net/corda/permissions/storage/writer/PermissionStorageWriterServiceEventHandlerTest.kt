package net.corda.permissions.storage.writer

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PermissionStorageWriterServiceEventHandlerTest {

    private val subscription = mock<RPCSubscription<PermissionManagementRequest, PermissionManagementResponse>>()
    private val subscriptionFactory = mock<SubscriptionFactory>().apply {
        whenever(createRPCSubscription(any(), any(), any<PermissionStorageWriterProcessor>())).thenReturn(subscription)
    }
    private val permissionStorageWriterProcessor = mock<PermissionStorageWriterProcessor>()
    private val permissionStorageWriterProcessorFactory = mock<PermissionStorageWriterProcessorFactory>().apply {
        whenever(create(any())).thenReturn(permissionStorageWriterProcessor)
    }
    private val handler = PermissionStorageWriterServiceEventHandler(
        mock(),
        subscriptionFactory,
        permissionStorageWriterProcessorFactory,
        mock()
    )

    @Test
    fun `processing a start event starts the writer's subscription`() {
        assertNull(handler.subscription)
        handler.processEvent(StartEvent(), mock())
        assertNotNull(handler.subscription)
        verify(subscription).start()
    }

    @Test
    fun `processing a stop event stops the permission storage writer`() {
        handler.processEvent(StartEvent(), mock())
        assertNotNull(handler.subscription)
        handler.processEvent(StopEvent(), mock())
        assertNull(handler.subscription)
        verify(subscription).stop()
    }
}