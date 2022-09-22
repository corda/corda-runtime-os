package net.corda.simulator.tools

import net.corda.simulator.exceptions.ServiceConfigurationException
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.annotations.DoNotImplement
import java.util.ServiceLoader

/**
 * Checks your [net.corda.v5.application.flows.Flow] for common mistakes with annotations and constructors.
 */
@DoNotImplement
interface FlowChecker {

    /**
     * @param flowClass the [Flow] to check.
     * @throws [net.corda.simulator.exceptions.NoDefaultConstructorException] if no default constructor was found
     * and was required.
     * @throws [net.corda.simulator.exceptions.NoProtocolAnnotationException] if no
     * [net.corda.v5.application.flows.InitiatingFlow] or [net.corda.v5.application.flows.InitiatedBy] annotation was
     * found with its accompanying protocol and was required.
     * @throws [net.corda.simulator.exceptions.UnrecognizedFlowClassException] if the flow does not inherit from a
     * recognized flow class.
     * @throws [net.corda.simulator.exceptions.NoSuspendableCallMethodException] if the flow is missing a
     * [net.corda.v5.base.annotations.Suspendable] annotation on the `call` method.
     */
    fun check(flowClass: Class<out Flow>)

    companion object {
        private val delegate = ServiceLoader.load(FlowChecker::class.java).first() ?:
            throw ServiceConfigurationException(FlowChecker::class.java)

        /**
         * Creates and runs a [FlowChecker] against the provided flow. See the [FlowChecker.check] method
         * for details of possible exceptions thrown by this class.
         *
         * @param flowClass The [Flow] to check.
         */
        fun check(flowClass: Class<out Flow>) {
            delegate.check(flowClass)
        }
    }
}