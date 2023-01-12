package net.corda.simulator.runtime.flows

import net.corda.simulator.exceptions.FlowClassNotFoundException
import net.corda.simulator.exceptions.NoDefaultConstructorException
import net.corda.simulator.exceptions.UnrecognizedFlowClassException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

/**
 * A simple utility class for creating initiating or responding flows while handling any errors.
 *
 * See [FlowFactory] for details of methods.
 */
class BaseFlowFactory : FlowFactory {

    override fun createInitiatingFlow(member: MemberX500Name, flowClassName: String): RestStartableFlow {
        val flowClass = Class.forName(flowClassName)
            ?: throw FlowClassNotFoundException(flowClassName)

        if (RestStartableFlow::class.java.isAssignableFrom(flowClass)) {
            return createFlow(flowClass)
        } else {
            throw UnrecognizedFlowClassException(flowClass, listOf(RestStartableFlow::class.java))
        }

    }

    override fun createResponderFlow(member: MemberX500Name, flowClass: Class<out Flow>): ResponderFlow {
        if (ResponderFlow::class.java.isAssignableFrom(flowClass)) {
            return createFlow(flowClass)
        } else {
            throw UnrecognizedFlowClassException(flowClass, listOf(ResponderFlow::class.java))
        }
    }

    private fun <T : Flow> createFlow(flowClass: Class<*>): T {
        val flowConstructor = flowClass.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw NoDefaultConstructorException(flowClass)
        @Suppress("UNCHECKED_CAST")
        return flowConstructor.newInstance() as T
    }
}