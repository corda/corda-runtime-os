package net.corda.flow.manager.impl.factory

import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.factory.FlowManagerFactory
import net.corda.flow.manager.impl.FlowManagerImpl
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.internal.di.DependencyInjectionService
import net.corda.sandbox.cache.SandboxCache
import net.corda.serialization.factory.CheckpointSerializationServiceFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class FlowManagerFactoryImpl @Activate constructor(
    @Reference(service = CheckpointSerializationServiceFactory::class)
    private val checkpointSerializationServiceFactory: CheckpointSerializationServiceFactory,
    @Reference(service = DependencyInjectionService::class)
    private val dependencyInjector: DependencyInjectionService,
    @Reference(service = FlowStateMachineFactory::class)
    private val flowStateMachineFactory: FlowStateMachineFactory
) : FlowManagerFactory {

    override fun createFlowManager(sandboxCache: SandboxCache): FlowManager {
        return FlowManagerImpl(
            sandboxCache,
            checkpointSerializationServiceFactory,
            dependencyInjector,
            flowStateMachineFactory
        )
    }
}