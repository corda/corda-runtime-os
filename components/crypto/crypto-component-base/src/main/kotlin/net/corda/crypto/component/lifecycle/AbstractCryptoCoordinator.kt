package net.corda.crypto.component.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractCryptoCoordinator(
    coordinatorName: LifecycleCoordinatorName,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val subcomponents: List<Any>
) : Lifecycle {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    private var coordinator = coordinatorFactory.createCoordinator(coordinatorName) { event, _ ->
        handleEvent(event)
    }

    private var configHandle: AutoCloseable? = null

    override fun start() {
        logger.info("Starting coordinator.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping coordinator.")
        subcomponents.forEach {
            if(it is AutoCloseable) {
                it.closeGracefully()
            }
        }
        coordinator.stop()
        configHandle?.closeGracefully()
    }

    protected open fun handleEvent(event: LifecycleEvent) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.closeGracefully()
                }
            }
            is NewCryptoConfigReceived -> {
                subcomponents.forEach {
                    if(it is Lifecycle && !it.isRunning) {
                        it.start()
                    }
                    if(it is CryptoLifecycleComponent) {
                        it.handleConfigEvent(event.config)
                    }
                }
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (ConfigKeys.CRYPTO_CONFIG in keys) {
            val newConfig = config[ConfigKeys.CRYPTO_CONFIG]
            val libraryConfig = if(newConfig == null || newConfig.isEmpty) {
                handleEmptyCryptoConfig()
            } else {
                CryptoLibraryConfigImpl(newConfig.root().unwrapped())
            }
            coordinator.postEvent(NewCryptoConfigReceived(libraryConfig))
        }
    }

    protected open fun handleEmptyCryptoConfig(): CryptoLibraryConfig {
        throw IllegalStateException("Configuration '${ConfigKeys.CRYPTO_CONFIG}' missing from map")
    }
}
