package org.corda.weft.dispatch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.corda.weft.binding.FacadeInterfaceBinding
import org.corda.weft.binding.FacadeMethodBinding
import org.corda.weft.binding.FacadeOutParameterBindings
import org.corda.weft.binding.api.BindsFacade
import org.corda.weft.binding.api.FacadeVersions
import org.corda.weft.binding.api.InteropAction
import org.corda.weft.binding.creation.FacadeInterfaceBindings
import org.corda.weft.facade.*
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue
import org.corda.weft.proxies.FacadeMethodDispatchException
import org.corda.weft.proxies.JacksonJsonMarshaller
import org.corda.weft.parameters.TypeConverter

object FacadeServerDispatchers {

    @JvmStatic
    fun <T : Any> buildDispatcher(
        facade: Facade,
        targetInterface: Class<T>,
        target: T,
        typeConverter: TypeConverter
    ) : FacadeServerDispatcher {
        val binding = FacadeInterfaceBindings.bind(facade, targetInterface)
        return FacadeServerDispatcher(typeConverter, binding, target)
    }
}
fun Any.buildDispatcher(facade: Facade, typeConverter: TypeConverter): FacadeServerDispatcher {
    val targetInterface = this.javaClass.interfaces.find {
        it.isAnnotationPresent(BindsFacade::class.java) &&
                it.getAnnotation(BindsFacade::class.java).value == facade.facadeId.unversionedName &&
                (!it.isAnnotationPresent(FacadeVersions::class.java) ||
                        facade.facadeId.version in it.getAnnotation(FacadeVersions::class.java).value.toSet())
    } ?: throw FacadeMethodDispatchException("Object $this implements no interface binding ${facade.facadeId}")
    @Suppress("UNCHECKED_CAST")
    return FacadeServerDispatchers.buildDispatcher(facade, targetInterface as Class<Any>, this, typeConverter)
}

fun Any.buildDispatcher(facade: Facade): FacadeServerDispatcher =
buildDispatcher(facade, TypeConverter(JacksonJsonMarshaller(ObjectMapper().registerKotlinModule())))

@Suppress("unchecked")
class FacadeServerDispatcher(
    val typeConverter: TypeConverter,
    val binding: FacadeInterfaceBinding,
    val target: Any
) : (FacadeRequest) -> FacadeResponse {
    override fun invoke(request: FacadeRequest): FacadeResponse {
        val boundMethod = binding.bindingFor(request.methodName) ?: throw FacadeMethodDispatchException(
            "No method on ${target.javaClass} is bound to request method ${request.methodName}"
        )

        val args = buildMethodArguments(boundMethod, request)
        @Suppress("UNCHECKED_CAST")
        val result = (boundMethod.interfaceMethod.invoke(target, *args) as InteropAction<Any>).result
        val outParameterValues = getOutParameterValues(result, boundMethod.outParameterBindings)

        return binding.facade.response(boundMethod.facadeMethod.name, *outParameterValues.toTypedArray())
    }

    @Suppress("UNCHECKED_CAST")
    private fun getOutParameterValues(
        result: Any,
        outParameterBindings: FacadeOutParameterBindings
    ): List<TypedParameterValue<*>> = when (outParameterBindings) {
        FacadeOutParameterBindings.NoOutParameters -> emptyList()

        is FacadeOutParameterBindings.SingletonOutParameterBinding -> {
            val parameter = outParameterBindings.outParameter as TypedParameter<Any>
            val value = typeConverter.convertJvmToFacade(result, parameter.type)

            listOf(parameter of value)
        }

        is FacadeOutParameterBindings.DataClassOutParameterBindings -> {
            outParameterBindings.bindings.map { binding ->
                val propertyValue = binding.readMethod.invoke(result)
                (binding.facadeOutParameter as TypedParameter<Any>) of propertyValue
            }
        }
    }

    private fun buildMethodArguments(
        boundMethod: FacadeMethodBinding,
        request: FacadeRequest
    ): Array<Any?> {
        val args = Array<Any?>(boundMethod.inParameterBindings.size) { null }

        request.inParameters.forEach { parameterValue ->
            val parameterBinding =
                boundMethod.bindingForInParameter(parameterValue.parameter.name) ?: throw FacadeMethodDispatchException(
                    "Method ${boundMethod.facadeMethod.qualifiedName} does not have a parameter " +
                            parameterValue.parameter.name
                )

            args[parameterBinding.boundParameter.index] = typeConverter.convertFacadeToJvm(
                parameterValue.parameter.type,
                parameterValue.value,
                parameterBinding.boundParameter.type
            )
        }
        return args
    }
}