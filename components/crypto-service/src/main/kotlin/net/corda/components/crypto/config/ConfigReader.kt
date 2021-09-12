package net.corda.components.crypto.config

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger

class ConfigReader(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val readServiceFactory: ConfigReadServiceFactory,
) : Lifecycle {
    companion object {
        private val logger = contextLogger()
        private const val CRYPTO_CONFIG: String = "corda.cryptoLibrary"
    }

    private var receivedSnapshot = false

    private var configReadService: ConfigReadService? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: Config? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: Config) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if (bootstrapConfig != null) {
            configReadService = readServiceFactory.createReadService(bootstrapConfig!!)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
                if (!receivedSnapshot) {
                    if (changedKeys.contains(CRYPTO_CONFIG)) {
                        logger.info("Config read service config snapshot received")
                        receivedSnapshot = true
                        lifeCycleCoordinator.postEvent(
                            CryptoConfigReceivedEvent(
                                getMyConfig(
                                    currentConfigurationSnapshot
                                )
                            )
                        )
                    }
                } else {
                    logger.info("Config read service config update received")
                    if (changedKeys.contains(CRYPTO_CONFIG)) {
                        logger.info("Config update contains kafka config")
                        lifeCycleCoordinator.postEvent(CryptoConfigUpdateEvent(getMyConfig(currentConfigurationSnapshot)))
                    }
                }
            }
            sub = configReadService!!.registerCallback(lister)
            configReadService!!.start()
        } else {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            logger.error(message)
            throw CordaRuntimeException(message)
        }
    }

    private fun getMyConfig(currentConfigurationSnapshot: Map<String, Config>): CryptoLibraryConfig =
        CryptoLibraryConfig(currentConfigurationSnapshot.getValue(CRYPTO_CONFIG))

    override fun stop() {
        sub?.close()
        sub = null
    }
}

interface CryptoConfigEvent : LifecycleEvent {
    val config: CryptoLibraryConfig
}

class CryptoConfigReceivedEvent(override val config: CryptoLibraryConfig) : CryptoConfigEvent

class CryptoConfigUpdateEvent(override val config: CryptoLibraryConfig) : CryptoConfigEvent
