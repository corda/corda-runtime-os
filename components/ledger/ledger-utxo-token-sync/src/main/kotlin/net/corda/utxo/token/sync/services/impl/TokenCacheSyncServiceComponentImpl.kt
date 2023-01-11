package net.corda.utxo.token.sync.services.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.schema.configuration.ConfigKeys
import net.corda.utxo.token.sync.TokenCacheSyncServiceComponent
import net.corda.utxo.token.sync.services.SyncConfiguration
import net.corda.utxo.token.sync.services.SyncWakeUpScheduler
import net.corda.utxo.token.sync.services.TokenCacheSyncSubscriptionHandler
import net.corda.utxo.token.sync.services.WakeUpGeneratorService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate

@Suppress("LongParameterList")
class TokenCacheSyncServiceComponentImpl @Activate constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val syncConfiguration: SyncConfiguration,
    private val wakeUpGeneratorService: WakeUpGeneratorService,
    private val syncWakeUpScheduler: SyncWakeUpScheduler,
    private val tokenCacheSubscriptionHandler: TokenCacheSyncSubscriptionHandler,
) : TokenCacheSyncServiceComponent {

    companion object {
        private val logger = contextLogger()
        private val configSections = setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
    }

    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private val coordinator = coordinatorFactory.createCoordinator<TokenCacheSyncServiceComponent>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "TokenCacheComponent received: $event" }
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting token cache component." }
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<TokenCacheSyncSubscriptionHandler>(),
                        )
                    )
                tokenCacheSubscriptionHandler.start()
            }

            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle?.close()
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        configSections
                    )
                } else {
                    coordinator.updateStatus(event.status)
                }
            }

            is ConfigChangedEvent -> {
                val config = event.config

                // notify the config dependent services a change was received.
                syncConfiguration.onConfigChange(config)
                wakeUpGeneratorService.onConfigChange(config)
                syncWakeUpScheduler.onConfigChange(config)
                tokenCacheSubscriptionHandler.onConfigChange(config)

                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                tokenCacheSubscriptionHandler.stop()
                logger.debug { "Stopping token cache component." }
                registration?.close()
                registration = null
            }
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
