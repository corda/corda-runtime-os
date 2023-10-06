package net.corda.flow.application.services.impl.interop.dispatch

import net.corda.flow.application.services.impl.interop.binding.FacadeInterfaceBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeMethodBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeOutParameterBindings
import net.corda.flow.application.services.impl.interop.binding.creation.FacadeInterfaceBindings
import net.corda.flow.application.services.impl.interop.parameters.TypeConverter
import net.corda.flow.application.services.impl.interop.proxies.FacadeMethodDispatchException
import net.corda.flow.application.services.impl.interop.proxies.JsonMarshaller
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue
import net.corda.v5.base.annotations.Suspendable

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

@Suppress("UNCHECKED_CAST")
fun Any.buildDispatcher(facade: Facade, typeConverter: TypeConverter): FacadeServerDispatcher {
    val targetInterface = this.javaClass.interfaces.find {
        it.isAnnotationPresent(BindsFacade::class.java) &&
                it.getAnnotation(BindsFacade::class.java).value == facade.facadeId.unversionedName &&
                (!it.isAnnotationPresent(FacadeVersions::class.java) ||
                        facade.facadeId.version in it.getAnnotation(FacadeVersions::class.java).value.toSet())
    } ?: throw FacadeMethodDispatchException("Object $this implements no interface binding ${facade.facadeId}")

    return FacadeServerDispatchers.buildDispatcher(facade, targetInterface as Class<Any>, this, typeConverter)
}

fun Any.buildDispatcher(facade: Facade, marshaller: JsonMarshaller): FacadeServerDispatcher =
    buildDispatcher(facade, TypeConverter(marshaller))

class FacadeServerDispatcher(
    val typeConverter: TypeConverter,
    val binding: FacadeInterfaceBinding,
    val target: Any
) : (FacadeRequest) -> FacadeResponse {
    @Suppress("SpreadOperator", "UNCHECKED_CAST")
    @Suspendable
    override fun invoke(request: FacadeRequest): FacadeResponse {
        val boundMethod = binding.bindingFor(request.methodName) ?: throw FacadeMethodDispatchException(
            "No method on ${target.javaClass} is bound to request method ${request.methodName}"
        )

        val args = buildMethodArguments(boundMethod, request)
        val result = boundMethod.interfaceMethod.invoke(target, *args)
        val outParameterValues = getOutParameterValues(result, boundMethod.outParameterBindings)

        return binding.facade.response(boundMethod.facadeMethod.name, *outParameterValues.toTypedArray())
    }

    @Suppress("UNCHECKED_CAST")
    @Suspendable
    private fun getOutParameterValues(
        result: Any?,
        outParameterBindings: FacadeOutParameterBindings
    ): List<TypedParameterValue<*>> = when (outParameterBindings) {
        FacadeOutParameterBindings.NoOutParameters -> emptyList()

        is FacadeOutParameterBindings.SingletonOutParameterBinding -> {
            val parameter = outParameterBindings.outParameter as TypedParameter<Any>
            val value = typeConverter.convertJvmToFacade(result!!, parameter.type.typeLabel)

            listOf(parameter.of(value))
        }

        is FacadeOutParameterBindings.DataClassOutParameterBindings -> {
            outParameterBindings.bindings.map { binding ->
                val propertyValue = binding.readMethod.invoke(result!!)
                (binding.facadeOutParameter as TypedParameter<Any>).of(propertyValue)
            }
        }
    }

    @Suspendable
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
                parameterValue.parameter.type.typeLabel,
                parameterValue.value,
                parameterBinding.boundParameter.type
            )
        }
        return args
    }
}