package net.corda.membership.impl.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.typesafe.config.Config
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.membership.config.MembershipConfig
import net.corda.membership.impl.config.MembershipConfigImpl
import net.corda.membership.lifecycle.MembershipLifecycleComponent

abstract class AbstractMembershipCoordinator(
    coordinatorName: LifecycleCoordinatorName,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReadService: ConfigurationReadService,
    private val subcomponents: List<Any>
) : Lifecycle {
    companion object {
        const val MEMBERSHIP_CONFIG: String = "membership"
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName) { event, _ ->
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
                it.close()
            }
        }
        coordinator.stop()
        configHandle?.close()
    }

    protected open fun handleEvent(event: LifecycleEvent) {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewMembershipConfigReceived -> {
                subcomponents.forEach {
                    if(it is Lifecycle && !it.isRunning) {
                        it.start()
                    }
                    if(it is MembershipLifecycleComponent) {
                        it.handleConfigEvent(event.config)
                    }
                }
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    }

    private fun onConfigChange(keys: Set<String>, config: Map<String, Config>) {
        if (MEMBERSHIP_CONFIG in keys) {
            val newConfig = config[MEMBERSHIP_CONFIG]
            val libraryConfig = if(newConfig == null || newConfig.isEmpty) {
                handleEmptyMembershipConfig()
            } else {
                MembershipConfigImpl(newConfig.root().unwrapped())
            }
            coordinator.postEvent(NewMembershipConfigReceived(libraryConfig))
        }
    }

    protected open fun handleEmptyMembershipConfig(): MembershipConfig {
        throw IllegalStateException("Configuration '$MEMBERSHIP_CONFIG' missing from map")
    }
}
