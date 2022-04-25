package net.corda.configuration.rpcops.impl

import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsInternal
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER

/** Processes configuration changes for `ConfigRPCOpsService`. */
internal class ConfigRPCOpsConfigHandler(
    private val coordinator: LifecycleCoordinator,
    private val configRPCOps: ConfigRPCOpsInternal
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
        if (RPC_CONFIG in changedKeys) processRPCConfig(config)
        if (configRPCOps.isRunning) coordinator.updateStatus(UP)
    }

    /**
     * If [RPC_ENDPOINT_TIMEOUT_MILLIS] is in [configSnapshot], updates [configRPCOps]'s request
     * timeout.
     *
     * If [BOOTSTRAP_SERVER] is in [configSnapshot], creates and starts [configRPCOps]'s RPC sender.
     *
     * @throws ConfigRPCOpsServiceException If [configSnapshot] does not contain any config for key [RPC_CONFIG], or if
     *  [configRPCOps]'s RPC sender could not be started.
     */
    private fun processRPCConfig(configSnapshot: Map<String, SmartConfig>) {
        val config = configSnapshot[RPC_CONFIG]?.withFallback(configSnapshot[BOOT_CONFIG]) ?: throw ConfigRPCOpsServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        if (config.hasPath(RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            val timeoutMillis = config.getInt(RPC_ENDPOINT_TIMEOUT_MILLIS)
            configRPCOps.setTimeout(timeoutMillis)
        }

        try {
            configRPCOps.createAndStartRPCSender(config)
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw ConfigRPCOpsServiceException(
                "Could not start the RPC sender for incoming HTTP RPC configuration management requests", e
            )
        }

    }
}