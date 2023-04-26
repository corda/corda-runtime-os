package net.corda.flow.application.services.impl.interop.facade

import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.TypedParameterValue

data class FacadeImpl(val facadeId: FacadeId, val methods: List<FacadeMethod>) : Facade {
    val methodsByName: Map<String, FacadeMethod> = methods.associateBy { it.name }
    override fun getFacadeId(): FacadeId {
        return facadeId
    }

    override fun getMethods(): List<FacadeMethod> {
        return methods
    }

    override fun getMethodsByName(): Map<String, FacadeMethod> {
        return methodsByName
    }

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
    override fun request(methodName: String?, vararg inParameters: TypedParameterValue<*>?): FacadeRequest {
        //TODO address !!
        return method(methodName!!).request(*inParameters)
    }

    /**
     * Obtain a response to an invocation of the method with the given name, with the given parameter values.
     *
     * @param methodName The name of the method that was invoked.
     * @param outParameters The values of the out parameters of the method.
     */
    override fun response(methodName: String?, vararg outParameters: TypedParameterValue<*>?): FacadeResponse {
        //TODO address !!
        return method(methodName!!).response(*outParameters)
    }
}