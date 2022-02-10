package net.corda.virtualnode.write.db.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory

/** Processes configuration changes for `VirtualNodeRPCOpsService`. */
internal class VirtualNodeWriteConfigHandler(
    private val eventHandler: VirtualNodeWriteEventHandler,
    private val coordinator: LifecycleCoordinator,
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : ConfigurationHandler {

    /**
     * When [RPC_CONFIG] configuration is received, initialises [eventHandler]'s virtual node writer and sets
     * [coordinator]'s status to UP if the relevant key is present.
     *
     * @throws VirtualNodeWriteServiceException If the virtual node writer has already been initialised, or could not
     *  be created or started.
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        val rpcConfig = config[RPC_CONFIG] ?: return

        if (rpcConfig.hasPath(BOOTSTRAP_SERVERS)) {
            if (eventHandler.virtualNodeWriter != null) throw VirtualNodeWriteServiceException(
                "An attempt was made to initialise the virtual node writer twice."
            )

            try {
                eventHandler.virtualNodeWriter = virtualNodeWriterFactory
                    // TODO - Set instance ID correctly.
                    .create(rpcConfig, 0)
                    .apply { start() }
                coordinator.updateStatus(UP)
            } catch (e: Exception) {
                coordinator.updateStatus(ERROR)
                throw VirtualNodeWriteServiceException(
                    "Could not start the virtual node writer for handling virtual node creation requests.", e
                )
            }
        }
    }
}