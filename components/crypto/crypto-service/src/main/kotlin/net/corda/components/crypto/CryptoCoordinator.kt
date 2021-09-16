package net.corda.components.crypto

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.config.ConfigReader
import net.corda.cipher.suite.impl.config.CryptoConfigReceivedEvent
import net.corda.cipher.suite.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.components.crypto.rpc.CryptoRpcSub
import net.corda.components.crypto.services.DefaultCryptoServiceProvider
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(immediate = true)
class CryptoCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    private val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = ConfigReadServiceFactory::class)
    private var configReadServiceFactory: ConfigReadServiceFactory,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(service = CryptoFactory::class)
    private val cryptoFactory: CryptoFactory,
    @Reference(service = DefaultCryptoServiceProvider::class)
    private val defaultCryptoServiceProvider: DefaultCryptoServiceProvider,
    @Reference(service = CryptoRpcSub::class)
    private val rpcSubs: List<CryptoRpcSub>
) : Lifecycle {
    companion object {
        private val logger = contextLogger()
    }

    override var isRunning: Boolean = false

    private var lifeCycleCoordinator: LifecycleCoordinator? = null

    private var configReader: ConfigReader? = null

    init {
        cryptoServiceLifecycleEventHandler.add { event, _ ->
            logger.info("LifecycleEvent received: $event")
            when (event) {
                is StartEvent -> {
                    logger.info("Starting crypto config reader")
                    configReader!!.start(getBootstrapConfig())
                }
                is CryptoConfigReceivedEvent -> {
                    defaultCryptoServiceProvider.start()
                    (cipherSuiteFactory as? Lifecycle)?.start() // TODO2 - add Lifecycle to the base of the CipherSuiteFactory
                    cryptoFactory.start()
                    rpcSubs.forEach { it.start() }
                    logger.info("Received config, started subscriptions")
                }
                is StopEvent -> {
                    configReader?.stop()
                }
            }
        }
    }

    override fun start() {
        logger.info("Starting coordinator.")
        lifeCycleCoordinator =
            lifecycleCoordinatorFactory.createCoordinator<CryptoCoordinator>(cryptoServiceLifecycleEventHandler)
        configReader = ConfigReader(lifeCycleCoordinator!!, configReadServiceFactory)
        logger.info("Starting life cycle coordinator")
        lifeCycleCoordinator!!.start()
        logger.info("Coordinator started.")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping coordinator.")
        isRunning = false
    }

    private fun getBootstrapConfig(): Config = ConfigFactory.empty()
}
