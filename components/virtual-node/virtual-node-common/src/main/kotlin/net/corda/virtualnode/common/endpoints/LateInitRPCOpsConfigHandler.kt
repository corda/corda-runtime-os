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

/** Sets [LateInitRPCOps] late init properties. */
internal class LateInitRPCOpsConfigHandler(
    private val coordinator: LifecycleCoordinator,
    private val lateInitRPCOps: LateInitRPCOps
) : ConfigurationHandler {

    /**
     * When [RPC_CONFIG] configuration is received, updates [lateInitRPCOps]'s request timeout and creates and
     * starts [lateInitRPCOps]'s RPC sender if the relevant keys are present.
     *
     * After updating [lateInitRPCOps], sets [coordinator]'s status to UP if [lateInitRPCOps] is running.
     *
     * @throws LateInitRPCOpsServiceException If [RPC_CONFIG] is in the [changedKeys], but no corresponding
     * configuration is provided in [config].
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (RPC_CONFIG in changedKeys) processRPCConfig(config)
        if (lateInitRPCOps.isRunning) coordinator.updateStatus(UP)
    }

    /**
     * If [RPC_ENDPOINT_TIMEOUT_MILLIS] is in [configSnapshot], updates [lateInitRPCOps]'s request
     * timeout.
     *
     * If [BOOTSTRAP_SERVERS] is in [configSnapshot], creates and starts [lateInitRPCOps]'s RPC sender.
     *
     * @throws LateInitRPCOpsServiceException If [configSnapshot] does not contain any config for key [RPC_CONFIG],
     * or if [lateInitRPCOps]'s RPC sender could not be started.
     */
    private fun processRPCConfig(configSnapshot: Map<String, SmartConfig>) {
        val config = configSnapshot[RPC_CONFIG] ?: throw LateInitRPCOpsServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        if (config.hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            val timeoutMillis = config.getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)
            lateInitRPCOps.setRpcRequestTimeout(Duration.ofMillis(timeoutMillis.toLong()))
        }

        if (config.hasPath(BOOTSTRAP_SERVERS)) {
            try {
                lateInitRPCOps.createAndStartRpcSender(config)
            } catch (e: Exception) {
                coordinator.updateStatus(ERROR)
                throw LateInitRPCOpsServiceException(
                    "Could not start the RPC sender for incoming HTTP RPC requests", e
                )
            }
        }
    }
}

class LateInitRPCOpsServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)