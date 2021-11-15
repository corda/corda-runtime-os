package net.corda.permissions.storage.writer

import net.corda.libs.permissions.storage.writer.PermissionStorageWriter
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.annotations.VisibleForTesting
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterServiceEventHandler(
    private val entityManagerFactory: EntityManagerFactory,
    private val permissionStorageWriterFactory: PermissionStorageWriterFactory
) : LifecycleEventHandler {

    @VisibleForTesting
    internal var permissionStorageWriter: PermissionStorageWriter? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                permissionStorageWriter = permissionStorageWriterFactory.create(entityManagerFactory)
                permissionStorageWriter?.start()
            }
            is StopEvent -> {
                permissionStorageWriter?.stop()
                permissionStorageWriter = null
            }
        }
    }
}