package net.corda.flow.fiber.factory

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowStackService
import net.corda.flow.fiber.FlowStackServiceImpl
import org.osgi.service.component.annotations.Component

@Component(service = [FlowStackServiceFactory::class])
@Suppress("Unused")
class FlowStackServiceFactoryImpl: FlowStackServiceFactory {

    override fun create(checkpoint: Checkpoint): FlowStackService {
        return FlowStackServiceImpl(checkpoint)
    }
}