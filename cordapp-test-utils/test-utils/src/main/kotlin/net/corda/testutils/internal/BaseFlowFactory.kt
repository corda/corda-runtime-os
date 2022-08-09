package net.corda.testutils.internal

import net.corda.testutils.exceptions.FlowClassNotFoundException
import net.corda.testutils.exceptions.NoDefaultConstructorException
import net.corda.testutils.exceptions.UnrecognizedFlowClassException
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name

/**
 * A simple utility class for creating initiating or responding flows while handling any errors.
 */
class BaseFlowFactory : FlowFactory {

    override fun createInitiatingFlow(x500: MemberX500Name, flowClassName: String): RPCStartableFlow {
        val flowClass = Class.forName(flowClassName)
            ?: throw FlowClassNotFoundException(flowClassName)

        if (!(RPCStartableFlow::class.java.isAssignableFrom(flowClass))) {
            throw UnrecognizedFlowClassException(flowClass, listOf(RPCStartableFlow::class.java))
        }
        return createFlow(flowClass) as RPCStartableFlow
    }

    override fun createResponderFlow(x500: MemberX500Name, flowClass: Class<out Flow>): ResponderFlow {
        if (!(ResponderFlow::class.java.isAssignableFrom(flowClass))) {
            throw UnrecognizedFlowClassException(flowClass, listOf(ResponderFlow::class.java))
        }
        return cast<ResponderFlow>(createFlow(flowClass))!!
    }

    private fun createFlow(flowClass: Class<*>): Any {
        val flowConstructor = flowClass.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw NoDefaultConstructorException(flowClass)
        return flowConstructor.newInstance()
    }
}