package net.corda.membership.impl.read.lifecycle

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.slf4j.LoggerFactory

/**
 * Lifecycle handler for the membership group read component.
 */
interface MembershipGroupReadLifecycleHandler : LifecycleEventHandler {
    /**
     * Default implementation.
     */
    class Impl(
        private val configurationReadService: ConfigurationReadService,
        private val activateImplFunction: (Map<String, SmartConfig>, String) -> Unit,
        private val deactivateImplFunction: (String) -> Unit
    ) : MembershipGroupReadLifecycleHandler {
        companion object {
            val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        private var configRegistrationHandle: AutoCloseable? = null
        private var dependencyRegistrationHandle: RegistrationHandle? = null

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is StartEvent -> {
                    dependencyRegistrationHandle?.close()
                    dependencyRegistrationHandle = coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
                }
                is StopEvent -> {
                    deactivateImplFunction.invoke("Stopped component due to StopEvent received.")
                    dependencyRegistrationHandle?.close()
                    configRegistrationHandle?.close()
                }
                is RegistrationStatusChangeEvent -> {
                    logger.info(MembershipGroupReaderProvider::class.simpleName + " handling registration changed event.")
                    // Respond to config read service lifecycle status change
                    when (event.status) {
                        LifecycleStatus.UP -> {
                            configRegistrationHandle?.close()
                            configRegistrationHandle = configurationReadService.registerComponentForUpdates(
                                coordinator,
                                setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                            )
                        }
                        else -> {
                            deactivateImplFunction.invoke("Component is inactive due to down dependency.")
                            configRegistrationHandle?.close()
                        }
                    }
                }
                is ConfigChangedEvent -> {
                    logger.info(MembershipGroupReaderProvider::class.simpleName + " handling new config event.")
                    activateImplFunction.invoke(
                        event.config,
                        "Starting component due to dependencies UP and configuration received."
                    )
                }
            }
        }
    }
}
