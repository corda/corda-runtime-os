package net.corda.virtualnode.write.db.impl

import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StopEvent
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeWriteServiceImpl]. */
internal class VirtualNodeWriteEventHandler(
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : LifecycleEventHandler {
    private var virtualNodeWriter: VirtualNodeWriter? = null

    /**
     * Upon [StartProcessingEvent], starts processing cluster configuration updates. Upon [StopEvent], stops processing
     * them.
     *
     * @throws VirtualNodeWriteServiceException If multiple [StartProcessingEvent]s are received, or if the creation of
     *  the subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartProcessingEvent -> {
                if (virtualNodeWriter != null) {
                    throw VirtualNodeWriteServiceException("An attempt was made to start processing twice.")
                }

                try {
                    // TODO - CORE-3316 - At worker start-up, read back configuration from database and check it
                    //  against Kafka topic.
                    virtualNodeWriter = virtualNodeWriterFactory
                        .create(event.config, event.instanceId, event.entityManagerFactory)
                        .apply { start() }
                } catch (e: Exception) {
                    coordinator.updateStatus(ERROR)
                    throw VirtualNodeWriteServiceException("Could not subscribe to virtual node creation requests.", e)
                }

                coordinator.updateStatus(UP)
            }

            is StopEvent -> {
                virtualNodeWriter?.stop()
                virtualNodeWriter = null
                coordinator.updateStatus(DOWN)
            }
        }
    }
}