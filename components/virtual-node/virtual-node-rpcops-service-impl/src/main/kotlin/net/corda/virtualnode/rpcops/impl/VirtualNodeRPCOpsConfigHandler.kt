package net.corda.virtualnode.rpcops.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsInternal

/** Processes configuration changes for `VirtualNodeRPCOpsService`. */
internal class VirtualNodeRPCOpsConfigHandler(
    private val coordinator: LifecycleCoordinator,
    private val virtualNodeRPCOps: VirtualNodeRPCOpsInternal
) : ConfigurationHandler {

    /**
     * When [RPC_CONFIG] configuration is received, updates [virtualNodeRPCOps]'s request timeout and creates and
     * starts [virtualNodeRPCOps]'s RPC sender if the relevant keys are present.
     *
     * After updating [virtualNodeRPCOps], sets [coordinator]'s status to UP if [virtualNodeRPCOps] is running.
     *
     * @throws VirtualNodeRPCOpsServiceException If [RPC_CONFIG] is in the [changedKeys], but no corresponding
     * configuration is provided in [config].
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (RPC_CONFIG in changedKeys) processRpcConfig(config)
        if (virtualNodeRPCOps.isRunning) coordinator.updateStatus(UP)
    }

    /**
     * If [RPC_ENDPOINT_TIMEOUT_MILLIS] is in [configSnapshot], updates [virtualNodeRPCOps]'s request
     * timeout.
     *
     * If [BOOTSTRAP_SERVERS] is in [configSnapshot], creates and starts [virtualNodeRPCOps]'s RPC sender.
     *
     * @throws VirtualNodeRPCOpsServiceException If [configSnapshot] does not contain any config for key [RPC_CONFIG],
     * or if [virtualNodeRPCOps]'s RPC sender could not be started.
     */
    private fun processRpcConfig(configSnapshot: Map<String, SmartConfig>) {
        val config = configSnapshot[RPC_CONFIG]?.withFallback(configSnapshot[BOOT_CONFIG]) ?: throw VirtualNodeRPCOpsServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        if (config.hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            val timeoutMillis = config.getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)
            virtualNodeRPCOps.setTimeout(timeoutMillis)
        }

        try {
            virtualNodeRPCOps.createAndStartRpcSender(config)
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw VirtualNodeRPCOpsServiceException(
                "Could not start the RPC sender for incoming HTTP RPC virtual node management requests", e
            )
        }

    }
}