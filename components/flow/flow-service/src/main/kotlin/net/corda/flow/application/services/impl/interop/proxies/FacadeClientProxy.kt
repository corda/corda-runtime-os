package net.corda.flow.application.services.impl.interop.proxies

import net.corda.flow.application.services.impl.interop.binding.FacadeInterfaceBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeMethodBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeOutParameterBindings
import net.corda.flow.application.services.impl.interop.binding.creation.FacadeInterfaceBindings
import net.corda.flow.application.services.impl.interop.parameters.TypeConverter
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterValueImpl
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

object FacadeProxies {

    /**
     * Entry point for generating a client proxy object for a [Facade] bound to a JVM interface.
     * @param facade The [Facade] to create a client proxy object for
     * @param interfaceType The [Class] of the JVM interface to create a client proxy object for
     * @param jsonMarshaller A [JsonMarshaller] to use when reading/writing JSON blobs
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getClientProxy(facade: Facade, interfaceType: Class<T>,
                           jsonMarshaller: JsonMarshaller,
                           requestProcessor: (FacadeRequest) -> FacadeResponse): T {
        val binding = FacadeInterfaceBindings.bind(facade, interfaceType)
        val proxy = FacadeClientProxy(binding, TypeConverter(jsonMarshaller), requestProcessor)
        return try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                Proxy.newProxyInstance(
                    interfaceType.classLoader,
                    arrayOf(interfaceType),
                    proxy
                ) as T
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }
}

/**
 * Kotlin convenience method for creating a client proxy with the provided [JsonMarshaller].
 */
inline fun <reified T : Any> Facade.getClientProxy(
    jsonMarshaller: JsonMarshaller,
    noinline requestProcessor: (FacadeRequest) -> FacadeResponse): T =
    FacadeProxies.getClientProxy(this, T::class.java, jsonMarshaller, requestProcessor)

/**
 * Kotlin convenience method for creating a client proxy with the provided [JsonMarshaller]
 * and the provided request processor.
 */
fun <T> Facade.getClientProxy(
    jsonMarshaller: JsonMarshaller,
    expectedClass: Class<T>,
    requestProcessor: (FacadeRequest) -> FacadeResponse): T =
    FacadeProxies.getClientProxy(this, expectedClass, jsonMarshaller, requestProcessor)

/**
 * Exception thrown if for some reason we can't dispatch a method call on a client proxy to create a [FacadeRequest],
 * or interpret a [FacadeResponse] to get a response value.
 * @param message The reason why dispatch failed.
 */
class FacadeMethodDispatchException(message: String) : RuntimeException(message)

// Here be dragons.
private class FacadeClientProxy(
    val binding: FacadeInterfaceBinding,
    val typeConverter: TypeConverter,
    val requestProcessor: (FacadeRequest) -> FacadeResponse) : InvocationHandler {

    @Suppress("UNCHECKED_CAST", "SpreadOperator")
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any? {
        val methodBinding = binding.bindingFor(method) ?: throw FacadeMethodDispatchException(
            "Binding of ${binding.boundInterface} to facade ${binding.facade.facadeId} has no binding for method " +
                    method.name)

        val parameterValues = args.indices.map { index ->
            val binding = methodBinding.bindingForMethodParameter(index)!!
            TypedParameterValueImpl(
                binding.facadeParameter as TypedParameter<Any>,
                typeConverter.convertJvmToFacade(args[index], binding.facadeParameter.type.typeLabel))
        }.toTypedArray()

        val request = methodBinding.facadeMethod.request(*parameterValues)
        return interpretResponse(requestProcessor.invoke(request), methodBinding)
    }

    @Suppress("UNCHECKED_CAST")
    private fun interpretResponse(response: FacadeResponse, binding: FacadeMethodBinding): Any? {
        validateResponse(response, binding)

        return when (val bindings = binding.outParameterBindings) {
            is FacadeOutParameterBindings.NoOutParameters -> null
            is FacadeOutParameterBindings.SingletonOutParameterBinding ->
                typeConverter.convertFacadeToJvm(
                    bindings.outParameter.type.typeLabel,
                    response[bindings.outParameter as TypedParameter<Any>],
                    bindings.returnType
                )

            is FacadeOutParameterBindings.DataClassOutParameterBindings ->
                buildDataClass(response.outParameters, bindings)
        }
    }

    private fun validateResponse(response: FacadeResponse, binding: FacadeMethodBinding) {
        if (response.facadeId != binding.facadeMethod.facadeId
            || response.methodName != binding.facadeMethod.name
        ) throw FacadeMethodDispatchException(
            "Facade method ${binding.facadeMethod.qualifiedName} was called, but received a response for " +
                    "${response.facadeId}/${response.methodName}"
        )

        val receivedParamSet = response.outParameters.asSequence().map(TypedParameterValue<*>::getParameter).toSet()
        val expectedParamSet = binding.facadeMethod.outParameters.toSet()

        if (receivedParamSet != expectedParamSet) {
            throw FacadeMethodDispatchException(
                "Expected response parameters $expectedParamSet, but received $receivedParamSet"
            )
        }
    }

    @Suppress("SpreadOperator")
    private fun buildDataClass(
        outParameters: List<TypedParameterValue<*>>,
        bindings: FacadeOutParameterBindings.DataClassOutParameterBindings): Any {
        val constructorArgs = Array<Any?>(bindings.bindings.size) { null }

        outParameters.forEach { outParameter ->
            val binding = bindings.bindingFor(outParameter.parameter)!!

            constructorArgs[binding.constructorParameter.index] = typeConverter.convertFacadeToJvm(
                outParameter.parameter.type.typeLabel,
                outParameter.value,
                binding.constructorParameter.type)
        }

        return bindings.constructor.newInstance(*constructorArgs)
    }
}

