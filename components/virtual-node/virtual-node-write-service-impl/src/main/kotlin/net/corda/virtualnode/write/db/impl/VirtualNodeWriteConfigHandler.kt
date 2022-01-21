package net.corda.virtualnode.write.db.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import javax.persistence.EntityManagerFactory

/** Processes configuration changes for `ConfigRPCOpsService`. */
internal class VirtualNodeWriteConfigHandler(
    private val eventHandler: VirtualNodeWriteEventHandler,
    private val coordinator: LifecycleCoordinator,
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : ConfigurationHandler {

    /**
     * When [RPC_CONFIG] configuration is received, updates [configRPCOps]'s request timeout and creates and starts
     * [configRPCOps]'s RPC sender if the relevant keys are present.
     *
     * After updating [configRPCOps], sets [coordinator]'s status to UP if [configRPCOps] is running.
     *
     * @throws ConfigRPCOpsServiceException If [RPC_CONFIG] is in the [changedKeys], but no corresponding configuration
     * is provided in [config].
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        println("JJJ processing config")
        if (RPC_CONFIG !in changedKeys) return

        val rpcConfig = config[RPC_CONFIG] ?: throw VirtualNodeWriteServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        if (rpcConfig.hasPath(BOOTSTRAP_SERVERS)) {
            println("JJJ setting up writer")
            if (eventHandler.virtualNodeWriter != null) {
                throw VirtualNodeWriteServiceException("An attempt was made to start processing twice.")
            }

            try {
                eventHandler.virtualNodeWriter = virtualNodeWriterFactory
                    // TODO - Joel - Choose instance ID correctly.
                    .create(rpcConfig, 0)
                    .apply { start() }
                coordinator.updateStatus(UP)
            } catch (e: Exception) {
                coordinator.updateStatus(ERROR)
                throw VirtualNodeWriteServiceException(
                    "Could not start the RPC sender for incoming HTTP RPC configuration management requests", e
                )
            }
        }
    }
}