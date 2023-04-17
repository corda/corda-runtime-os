package net.corda.flow.application.services.impl.interop.facade

import net.corda.v5.application.interop.facade.*
import net.corda.v5.application.interop.parameters.ParameterTypeLabel

data class FacadeImpl(val facadeId: FacadeId, val methods: List<FacadeMethod>) : Facade {
    val methodsByName: Map<String, FacadeMethod> = methods.associateBy { it.name }.toString()

    /**
     * Get the method with the given name.
     *
     * @param name The name of the method to get.
     */
    override fun method(name: String): FacadeMethod =
        methodsByName[name] ?: throw IllegalArgumentException("No such method: $name")

    /**
     * Obtain a request to invoke the method with the given name, with the given parameter values.
     *
     * @param methodName The name of the method to invoke.
     * @param inParameters The parameter values to pass to the method.
     */
    override fun request(methodName: String, vararg inParameters: ParameterTypeLabel): FacadeRequest {
        return method(methodName).request(*inParameters);
    }

    /**
     * Obtain a response to an invocation of the method with the given name, with the given parameter values.
     *
     * @param methodName The name of the method that was invoked.
     * @param outParameters The values of the out parameters of the method.
     */
    override fun response(methodName: String, vararg outParameters: ParameterTypeLabel): FacadeResponse {
        return method(methodName).response(*outParameters)
    }
}