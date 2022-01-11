package net.corda.configuration.rpcops.impl

import com.typesafe.config.ConfigException
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG

/** Processes configuration changes for `ConfigRPCOpsService`. */
internal class ConfigRPCOpsConfigHandler(
    private val coordinator: LifecycleCoordinator,
    private val configRPCOps: ConfigRPCOps
) : ConfigurationHandler {

    /**
     * When [BOOT_CONFIG] configuration is received, starts [configRPCOps]'s RPC sender.
     *
     * When [RPC_CONFIG] configuration is received, if [CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS] is in [config], updates
     * [configRPCOps]'s request timeout.
     *
     * After updating [configRPCOps], sets [coordinator]'s status to UP if [configRPCOps] is running.
     *
     * @throws ConfigRPCOpsServiceException If [BOOT_CONFIG] or [RPC_CONFIG] are in the [changedKeys], but no
     *  corresponding configuration is provided in [config].
     */
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (BOOT_CONFIG in changedKeys) processBootConfig(config)
        if (RPC_CONFIG in changedKeys) processRPCConfig(config)
        if (configRPCOps.isRunning) coordinator.updateStatus(UP)
    }

    /**
     * Starts [configRPCOps]'s RPC sender.
     *
     * @throws ConfigRPCOpsServiceException If there is no configuration for [BOOT_CONFIG] in [currentConfigSnapshot].
     */
    private fun processBootConfig(currentConfigSnapshot: Map<String, SmartConfig>) {
        val config = currentConfigSnapshot[BOOT_CONFIG] ?: throw ConfigRPCOpsServiceException(
            "Was notified of an update to configuration key $BOOT_CONFIG, but no such configuration was found."
        )

        try {
            configRPCOps.createAndStartRPCSender(config)
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw ConfigRPCOpsServiceException(
                "Could not start the RPC sender for incoming HTTP RPC configuration management requests", e
            )
        }
    }

    /**
     * If [CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS] is in [currentConfigSnapshot], updates [configRPCOps]'s request
     * timeout.
     *
     * @throws ConfigRPCOpsServiceException If there is no configuration for [RPC_CONFIG] in [currentConfigSnapshot].
     */
    private fun processRPCConfig(currentConfigSnapshot: Map<String, SmartConfig>) {
        val config = currentConfigSnapshot[RPC_CONFIG] ?: throw ConfigRPCOpsServiceException(
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found."
        )

        try {
            val timeoutMillis = config.getInt(CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS)
            configRPCOps.setTimeout(timeoutMillis)
        } catch (_: ConfigException) {
        }
    }
}