package net.corda.flow.manager.impl.fiber

import co.paralleluniverse.strands.Strand
import net.corda.flow.manager.fiber.FlowFiber
import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component

@Component(service = [FlowFiberService::class, SingletonSerializeAsToken::class])
class FlowFiberServiceImpl: FlowFiberService, SingletonSerializeAsToken {

    override fun getExecutingFiber(): FlowFiber<*> {
        val strand = checkNotNull(Strand.currentStrand()) { "This call should only be made from within a running fiber."}
        return checkNotNull(strand as FlowFiber<*>) { "The running fiber does not implement the FlowFiber interface"}
    }
}