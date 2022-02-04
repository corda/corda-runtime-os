package net.corda.virtualnode.common.endpoints

import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.Companion.BOOTSTRAP_SERVERS
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Duration

/** Processes configuration changes for `VirtualNodeRPCOpsService`. */
internal class RPCOpsConfigHandler(
    private val coordinator: LifecycleCoordinator,
    private val cpiUploadRPCOps: LateInitRPCOps
) : ConfigurationHandler {

    /**
     * When [RPC_CONFIG] configuration is received, updates [cpiUploadRPCOps]'s request timeout and creates and
     * starts [cpiUploadRPCOps]'s RPC sender if the relevant keys are present.
     *
     * After updating [cpiUploadRPCOps], sets [coordinator]'s status to UP if [cpiUploadRPCOps] is running.
     *
     * @throws LateInitRPCOpsServiceException If [RPC_CONFIG] is in the [changedKeys], but no corresponding
     * configuration is provided in [config].
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (RPC_CONFIG in changedKeys) processRPCConfig(config)
        if (cpiUploadRPCOps.isRunning) coordinator.updateStatus(UP)
    }

    /**
     * If [RPC_ENDPOINT_TIMEOUT_MILLIS] is in [configSnapshot], updates [cpiUploadRPCOps]'s request
     * timeout.
     *
     * If [BOOTSTRAP_SERVERS] is in [configSnapshot], creates and starts [cpiUploadRPCOps]'s RPC sender.
     *
     * @throws VirtualNodeRPCOpsServiceException If [configSnapshot] does not contain any config for key [RPC_CONFIG],
     * or if [cpiUploadRPCOps]'s RPC sender could not be started.
     */
    private fun processRPCConfig(configSnapshot: Map<String, SmartConfig>) {
        val config = configSnapshot[RPC_CONFIG] ?: throw RPCOpsServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        if (config.hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            val timeoutMillis = config.getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)
            cpiUploadRPCOps.setHttpRequestTimeout(Duration.ofMillis(timeoutMillis.toLong()))
        }

        if (config.hasPath(BOOTSTRAP_SERVERS)) {
            try {
                cpiUploadRPCOps.createRpcSender(config)
            } catch (e: Exception) {
                coordinator.updateStatus(ERROR)
                throw RPCOpsServiceException(
                    "Could not start the RPC sender for incoming HTTP RPC virtual node management requests", e
                )
            }
        }
    }
}

class RPCOpsServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)