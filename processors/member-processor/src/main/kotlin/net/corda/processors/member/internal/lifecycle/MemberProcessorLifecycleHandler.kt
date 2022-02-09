package net.corda.processors.member.internal.lifecycle

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.processors.member.internal.BootConfigEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class MemberProcessorLifecycleHandler(
    private val configurationReadService: ConfigurationReadService,
    private val dependentComponents: DependentComponents
) : LifecycleEventHandler {
    companion object {
        val logger = contextLogger()
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Member processor received event $event." }
        when (event) {
            is StartEvent -> {
                logger.info("Starting Member processor.")
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Member processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                logger.info("Member processor received boot configuration.")
                configurationReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                logger.info("Member processor stopping.")
                dependentComponents.stopAll()
            }
        }
    }
}