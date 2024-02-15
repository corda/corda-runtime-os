package net.corda.flow.service

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.messaging.mediator.FlowEventMediatorFactory
import net.corda.flow.messaging.mediator.fakes.TestConfig
import net.corda.flow.messaging.mediator.fakes.TestLoadGenerator
import net.corda.flow.messaging.mediator.fakes.TestStateManagerFactoryImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.schema.configuration.StateManagerConfig
import net.corda.utilities.trace
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [FlowExecutor::class])
class FlowExecutorImpl @Activate constructor(
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = FlowEventMediatorFactory::class)
    private val flowEventMediatorFactory: FlowEventMediatorFactory,
) : FlowExecutor {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowExecutor> { event, _ -> eventHandler(event) }
    private var multiSourceEventMediator: MultiSourceEventMediator<String, Checkpoint, FlowEvent>? = null

    private val bobX500 = "CN=Bob-5a90563f-73a0-46ce-a7e4-28354b6c686c, OU=Application, O=R3, L=London, C=GB"
    private val groupId = "d1f30558-4627-495c-8ea5-2fb4b9273c74"

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        try {
            multiSourceEventMediator?.close()

            // Wait for our test vNode to become available
            val vNodeHoldingID = net.corda.virtualnode.HoldingIdentity(MemberX500Name.parse(bobX500), groupId)
            while (virtualNodeInfoReadService.get(vNodeHoldingID) == null) {
                Thread.sleep(5000) // Wait 5 seconds and recheck
            }

            val configs = TestConfig().toSmartConfigs().toMutableMap()
            configs.forEach { (key, cfg) -> configs[key] = cfg.withFallback(config[key]) }

            val holdingId = HoldingIdentity(bobX500, groupId)

            val loadGenerator = TestLoadGenerator(
                "test-cordapp",
                holdingId,
                "com.r3.corda.testing.smoketests.flow.RestSmokeTestFlow",
                "{\"command\":\"crypto_sign_and_verify\",\"data\":{\"memberX500\":\"CN=Bob-5a90563f-73a0-46ce-a7e4-28354b6c686c, OU=Application, O=R3, L=London, C=GB\"}}"
            )
            val stateManager = TestStateManagerFactoryImpl(coordinatorFactory)
                .create(SmartConfigImpl.empty(), StateManagerConfig.StateType.FLOW_CHECKPOINT)
            multiSourceEventMediator = flowEventMediatorFactory.create(configs, loadGenerator, stateManager)
            stateManager.start()
            multiSourceEventMediator?.start()
        } catch (ex: Exception) {
            val reason = "Failed to configure the flow executor using '${config}'"
            log.error(reason, ex)
            coordinator.updateStatus(LifecycleStatus.ERROR, reason)
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

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                log.trace { "Flow executor is stopping..." }
                multiSourceEventMediator?.close()
                log.trace { "Flow executor stopped" }
            }
        }
    }
}
