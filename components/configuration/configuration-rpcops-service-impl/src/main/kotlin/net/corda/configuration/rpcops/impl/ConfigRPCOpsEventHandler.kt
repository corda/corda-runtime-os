package net.corda.configuration.rpcops.impl

import com.typesafe.config.ConfigException
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.RPC_CONFIG

/** Handles incoming [LifecycleCoordinator] events for [ConfigRPCOpsServiceImpl]. */
internal class ConfigRPCOpsEventHandler(
    private val configRPCOpsService: ConfigRPCOpsServiceImpl,
    private val configReadService: ConfigurationReadService,
    private val configRPCOps: ConfigRPCOps
) : LifecycleEventHandler {
    private var configReadServiceRegistration: RegistrationHandle? = null
    private var configReadServiceHandle: AutoCloseable? = null

    /**
     * Upon receiving configuration from [configReadService], start handling RPC operations related to cluster
     * configuration management.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> trackConfigReadService()
            is RegistrationStatusChangeEvent -> tryRegisteringForConfigUpdates(event)
            is StopEvent -> stop()
        }
    }

    /** Starts tracking the status of the [ConfigurationReadService]. */
    private fun trackConfigReadService() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = configRPCOpsService.coordinator.followStatusChangesByName(
            setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
        )
    }

    /** If the [ConfigurationReadService] comes up, registers to receive updates. */
    private fun tryRegisteringForConfigUpdates(event: RegistrationStatusChangeEvent) {
        if (event.registration == configReadServiceRegistration && event.status == UP) {
            configReadServiceHandle?.close()
            configReadServiceHandle = configReadService.registerForUpdates(::processConfigUpdate)
        }
    }

    /** Shuts down [configRPCOpsService]. */
    private fun stop() {
        configRPCOps.close()
        configRPCOpsService.coordinator.updateStatus(DOWN)
    }

    /**
     * When [BOOT_CONFIG] configuration is received, starts [configRPCOps]'s RPC sender.
     *
     * When [RPC_CONFIG] configuration is received, if [CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS] is in
     * [currentConfigSnapshot], updates [configRPCOps]'s request timeout.
     *
     * After updating [configRPCOps], sets [configReadService]'s status to UP if [configRPCOps] is running.
     *
     * @throws ConfigRPCOpsServiceException If [BOOT_CONFIG] or [RPC_CONFIG] are in the [changedKeys], but no
     *  corresponding configuration is provided in [currentConfigSnapshot].
     */
    private fun processConfigUpdate(changedKeys: Set<String>, currentConfigSnapshot: Map<String, SmartConfig>) {
        if (BOOT_CONFIG in changedKeys) processBootConfig(currentConfigSnapshot)
        if (RPC_CONFIG in changedKeys) processRPCConfig(currentConfigSnapshot)
        if (configRPCOpsService.isRunning) configRPCOpsService.coordinator.updateStatus(UP)
    }

    /**
     * Starts [configRPCOps]'s RPC sender.
     *
     * @throws ConfigRPCOpsServiceException If there is no configuration for [BOOT_CONFIG] in [currentConfigSnapshot].
     */
    private fun processBootConfig(currentConfigSnapshot: Map<String, SmartConfig>) {
        val config = currentConfigSnapshot[BOOT_CONFIG] ?: throw ConfigRPCOpsServiceException(
            "Was notified of an update to configuration key $BOOT_CONFIG, but no such configuration was found. "
        )
        try {
            configRPCOps.createAndStartRPCSender(config)
        } catch (e: Exception) {
            configRPCOpsService.coordinator.updateStatus(ERROR)
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
            "Was notified of an update to configuration key $RPC_CONFIG, but no such configuration was found. "
        )
        try {
            val timeoutMillis = config.getInt(CONFIG_KEY_CONFIG_RPC_TIMEOUT_MILLIS)
            configRPCOps.setTimeout(timeoutMillis)
        } catch (_: ConfigException) {
        }
    }
}