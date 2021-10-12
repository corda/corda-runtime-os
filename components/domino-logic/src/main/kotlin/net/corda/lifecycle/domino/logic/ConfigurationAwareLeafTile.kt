package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class ConfigurationAwareLeafTile<C>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService,
    private val key: String,
    private val configFactory: (Config) -> C
) :
    DominoTile(coordinatorFactory) {

    companion object {
        private val logger = contextLogger()
    }

    private val configurationHolder = AtomicReference<C>()
    private val canReceiveConfigurations = AtomicBoolean(false)
    protected val resources = ResourcesHolder()

    private inner class Handler : ConfigurationHandler {
        override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
            if (changedKeys.contains(key)) {
                val newConfiguration = config[key]
                if (newConfiguration != null) {
                    applyNewConfiguration(newConfiguration)
                }
            }
        }
    }

    private val registration = AtomicReference<AutoCloseable>(null)

    override fun close() {
        registration.getAndSet(null)?.close()
        super.close()
    }

    private fun applyNewConfiguration(newConfiguration: Config) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val configuration = configFactory(newConfiguration)
            logger.info("Got configuration $name")
            val oldConfiguration = configurationHolder.getAndSet(configuration)
            if (oldConfiguration == configuration) {
                logger.info("Configuration had not changed $name")
                return
            } else if ((state == State.StoppedDueToError) || (canReceiveConfigurations.get())) {
                logger.info("Reconfiguring $name")
                applyNewConfiguration(configuration, oldConfiguration)
                canReceiveConfigurations.set(true)
                started()
                logger.info("Reconfigured $name")
            }
        } catch (e: Throwable) {
            gotError(e)
        }
    }

    abstract fun applyNewConfiguration(newConfiguration: C, oldConfiguration: C?)

    override fun startTile() {
        if(registration.get() == null) {
            registration.getAndSet(
                configurationReaderService.registerForUpdates(Handler()))
                ?.close()
        }
        if (configurationHolder.get() != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                applyNewConfiguration(configurationHolder.get(), null)
                started()
            } catch (e: Throwable) {
                gotError(e)
            }
        }

        canReceiveConfigurations.set(true)
        resources.keep {
            canReceiveConfigurations.set(false)
        }
    }

    override fun stopTile() {
        resources.close()
    }
}
