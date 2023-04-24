package org.corda.weft.proxies

import net.corda.v5.application.marshalling.JsonMarshallingService
import org.corda.weft.api.JsonMarshaller
import org.corda.weft.binding.FacadeInterfaceBinding
import org.corda.weft.binding.FacadeMethodBinding
import org.corda.weft.binding.FacadeOutParameterBindings
import org.corda.weft.binding.api.InteropAction
import org.corda.weft.binding.creation.FacadeInterfaceBindings
import org.corda.weft.facade.*
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue
import org.corda.weft.parameters.TypeConverter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object FacadeProxies {

    /**
     * Entry point for generating a client proxy object for a [Facade] bound to a JVM interface.
     *
     * @param facade The [Facade] to create a client proxy object for
     * @param interfaceType The [Class] of the JVM interface to create a client proxy object for
     * @param jsonMarshaller A [JsonMarshaller] to use when reading/writing JSON blobs
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T> getClientProxy(facade: Facade, interfaceType: Class<T>,
                           jsonMarshaller: JsonMarshaller,
                           requestProcessor: (FacadeRequest) -> FacadeResponse): T {
        val binding = FacadeInterfaceBindings.bind(facade, interfaceType)
        val proxy = FacadeClientProxy(binding, TypeConverter(jsonMarshaller), requestProcessor)

        return Proxy.newProxyInstance(
            interfaceType.classLoader,
            arrayOf(interfaceType),
            proxy
        ) as T
    }

}

/**
 * Kotlin convenience method for creating a client proxy with a default [JsonMarshaller].
 */
inline fun <reified T : Any> Facade.getClientProxy(serializer: JsonMarshallingService, noinline requestProcessor: (FacadeRequest) -> FacadeResponse) =
    getClientProxy<T>(
        JacksonJsonMarshaller(serializer),
        requestProcessor)

/**
 * Kotlin convenience method for creating a client proxy with the provided [JsonMarshaller].
 */
inline fun <reified T : Any> Facade.getClientProxy(
    jsonMarshaller: JsonMarshaller,
    noinline requestProcessor: (FacadeRequest) -> FacadeResponse): T =
    FacadeProxies.getClientProxy(this, T::class.java, jsonMarshaller, requestProcessor)

/**
 * Exception thrown if for some reason we can't dispatch a method call on a client proxy to create a [FacadeRequest],
 * or interpret a [FacadeResponse] to get a response value.
 *
 * @param message The reason why dispatch failed.
 */
class FacadeMethodDispatchException(message: String) : RuntimeException(message)

// Here be dragons.
private class FacadeClientProxy(
    val binding: FacadeInterfaceBinding,
    val typeConverter: TypeConverter,
    val requestProcessor: (FacadeRequest) -> FacadeResponse) : InvocationHandler {

    @Suppress("UNCHECKED_CAST")
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>): Any {
        val methodBinding = binding.bindingFor(method) ?: throw FacadeMethodDispatchException(
            "Binding of ${binding.boundInterface} to facade ${binding.facade.facadeId} has no binding for method " +
                    method.name)

        val parameterValues = args.indices.map { index ->
            val binding = methodBinding.bindingForMethodParameter(index)!!
            TypedParameterValue(
                binding.facadeParameter as TypedParameter<Any>,
                typeConverter.convertJvmToFacade(args[index], binding.facadeParameter.type))
        }.toTypedArray()

        val request = methodBinding.facadeMethod.request(*parameterValues)
        return InteropAction.ClientAction(request, requestProcessor) { interpretResponse(it, methodBinding) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun interpretResponse(response: FacadeResponse, binding: FacadeMethodBinding): Any? {
        validateResponse(response, binding)

        return when (val bindings = binding.outParameterBindings) {
            is FacadeOutParameterBindings.NoOutParameters -> null
            is FacadeOutParameterBindings.SingletonOutParameterBinding ->
                typeConverter.convertFacadeToJvm(
                    bindings.outParameter.type,
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

        val receivedParamSet = response.outParameters.asSequence().map(TypedParameterValue<*>::parameter).toSet()
        val expectedParamSet = binding.facadeMethod.outParameters.toSet()

        if (receivedParamSet != expectedParamSet) {
            throw FacadeMethodDispatchException(
                "Expected response parameters $expectedParamSet, but received $receivedParamSet"
            )
        }
    }

    private fun buildDataClass(
        outParameters: List<TypedParameterValue<*>>,
        bindings: FacadeOutParameterBindings.DataClassOutParameterBindings): Any {
        val constructorArgs = Array<Any?>(bindings.bindings.size) { null }

        outParameters.forEach { outParameter ->
            val binding = bindings.bindingFor(outParameter.parameter)!!

            constructorArgs[binding.constructorParameter.index] = typeConverter.convertFacadeToJvm(
                outParameter.parameter.type,
                outParameter.value,
                binding.constructorParameter.type)
        }

        return bindings.constructor.newInstance(*constructorArgs)
    }
}

