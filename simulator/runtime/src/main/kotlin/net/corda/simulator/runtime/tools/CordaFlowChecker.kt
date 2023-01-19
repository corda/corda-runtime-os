package net.corda.simulator.runtime.tools

import net.corda.simulator.exceptions.NoDefaultConstructorException
import net.corda.simulator.exceptions.NoSuspendableCallMethodException
import net.corda.simulator.exceptions.UnrecognizedFlowClassException
import net.corda.simulator.tools.FlowChecker
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

/**
 * @see [FlowChecker] for details.
 */
class CordaFlowChecker : FlowChecker {
    override fun check(flowClass: Class<out Flow>) {

        val callMethod = if (SubFlow::class.java.isAssignableFrom(flowClass)) {
            flowClass.getMethod("call")
        } else {
            flowClass.constructors.firstOrNull { it.parameterCount == 0 }
                ?: throw NoDefaultConstructorException(flowClass)

            if (ResponderFlow::class.java.isAssignableFrom(flowClass)) {
                flowClass.getMethod("call", FlowSession::class.java)
            } else if (ClientStartableFlow::class.java.isAssignableFrom(flowClass)) {
                flowClass.getMethod("call", RestRequestBody::class.java)
            } else  {
                throw UnrecognizedFlowClassException(flowClass, listOf(
                    ResponderFlow::class.java,
                    ClientStartableFlow::class.java,
                    SubFlow::class.java
                ))
            }
        }

        callMethod.getAnnotation(Suspendable::class.java)
            ?: throw NoSuspendableCallMethodException(flowClass)
    }
}
