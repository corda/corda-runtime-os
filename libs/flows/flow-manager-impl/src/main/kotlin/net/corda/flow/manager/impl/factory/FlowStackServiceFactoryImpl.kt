package net.corda.flow.manager.impl.factory

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowStackService
import net.corda.flow.manager.factory.FlowStackServiceFactory
import net.corda.flow.manager.impl.FlowStackServiceImpl
import org.osgi.service.component.annotations.Component

@Component(service = [FlowStackServiceFactory::class])
@Suppress("Unused")
class FlowStackServiceFactoryImpl: FlowStackServiceFactory {

    override fun create(checkpoint: Checkpoint): FlowStackService {
        return FlowStackServiceImpl(checkpoint)
    }
}