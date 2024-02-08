package net.corda.flow.rest.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.rest.FlowRestResourceService
import net.corda.flow.rest.FlowStatusLookupService
import net.corda.flow.rest.v1.FlowRestResource
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.REST_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** An implementation of [FlowRestResourceService]. */
@Component(service = [FlowRestResourceService::class])
internal class FlowRestResourceServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = FlowRestResource::class)
    private val flowRestResource: FlowRestResource,
    @Reference(service = FlowStatusLookupService::class)
    private val flowStatusLookupService: FlowStatusLookupService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory
) : FlowRestResourceService {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var isUp = false

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowRestResourceService>(::eventHandler)
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::virtualNodeInfoReadService,
        ::flowStatusLookupService
    )

    override val isRunning get() = lifecycleCoordinator.isRunning

    override fun start() = lifecycleCoordinator.start()

    override fun stop() = lifecycleCoordinator.stop()

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
            }
            is RegistrationStatusChangeEvent -> {
                configurationReadService.registerComponentForUpdates(
                    lifecycleCoordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG, STATE_MANAGER_CONFIG, REST_CONFIG)
                )
            }
            is ConfigChangedEvent -> {
                with(event.config) {
                    flowRestResource.initialise(getConfig(MESSAGING_CONFIG), ::signalErrorStatus)
                    flowStatusLookupService.initialise(
                        getConfig(MESSAGING_CONFIG),
                        getConfig(STATE_MANAGER_CONFIG),
                        getConfig(REST_CONFIG)
                    )
                }
            }
            is StopEvent -> {
                flowStatusLookupService.stop()
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }

    private fun signalErrorStatus() {
        lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR)
    }
}
