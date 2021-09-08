package net.corda.httprpc.server.apigen.processing

import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.server.apigen.models.EndpointParameter
import net.corda.httprpc.server.apigen.models.ParameterType
import net.corda.httprpc.server.apigen.models.GenericParameterizedType
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.httprpc.api.annotations.isHttpRpcParameterAnnotation
import net.corda.v5.httprpc.tools.annotations.extensions.name
import java.lang.reflect.Parameter
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.jvmErasure

/**
 * [ParametersTransformer] implementations are responsible for transforming values into [EndpointParameter].
 *
 */
internal interface ParametersTransformer {
    fun transform(): EndpointParameter
}

/**
 * [ParametersTransformerFactory] is responsible for creating an instance of [ParametersTransformer]
 * that can transform the provided [Parameter].
 */
internal object ParametersTransformerFactory {
    fun create(name: String, type: GenericParameterizedType): ParametersTransformer =
        BodyParametersExplicitTransformer(name, type)

    fun create(param: KParameter) =
        param.annotations.singleOrNull { it.isHttpRpcParameterAnnotation() }.let {
            when (it) {
                is HttpRpcPathParameter -> PathParametersTransformer(param, it)
                is HttpRpcQueryParameter -> QueryParametersTransformer(param, it)
                is HttpRpcRequestBodyParameter -> BodyParametersTransformer(param, it)
                else -> BodyParametersTransformer(param, null)
            }
        }
}

private class PathParametersTransformer(
    private val parameter: KParameter,
    private val annotation: HttpRpcPathParameter
) : ParametersTransformer {
    private companion object {
        private val log = contextLogger()
    }

    override fun transform(): EndpointParameter {
        log.trace { "Transform path parameter \"${parameter.name}\"" }
        return EndpointParameter(
            id = parameter.name!!,
            name = annotation.name(parameter.name!!),
            description = annotation.description,
            required = true,
            nullable = parameter.type.isMarkedNullable,
            default = null,
            classType = parameter.type.jvmErasure.java,
            parameterizedTypes = parameter.getParameterizedTypes(),
            type = ParameterType.PATH
        ).also { log.trace { "Transform path parameter \"${parameter.name}\" completed. Result:\n$it" } }
    }
}

private class QueryParametersTransformer(
    private val parameter: KParameter,
    private val annotation: HttpRpcQueryParameter
) : ParametersTransformer {
    private companion object {
        private val log = contextLogger()
    }

    override fun transform(): EndpointParameter {
        log.trace { "Transform query parameter \"${parameter.name}\"" }
        return EndpointParameter(
            id = parameter.name!!,
            name = annotation.name(parameter.name!!),
            description = annotation.description,
            required = annotation.required,
            nullable = parameter.type.isMarkedNullable,
            default = annotation.default.ifBlank { null },
            classType = parameter.type.jvmErasure.java,
            parameterizedTypes = parameter.getParameterizedTypes(),
            type = ParameterType.QUERY
        ).also { log.trace { "Transform query parameter \"${parameter.name}\" completed. Result:\n$it" } }
    }
}

@VisibleForTesting
internal class BodyParametersTransformer(
    private val parameter: KParameter,
    private val annotation: HttpRpcRequestBodyParameter?
) : ParametersTransformer {
    private companion object {
        private val log = contextLogger()
    }

    override fun transform(): EndpointParameter {
        log.trace { "Transform body parameter \"${parameter.name}\"" }
        return if (annotation != null) {
            transformWithAnnotation(annotation).also { log.trace { "Transform body parameter \"${parameter.name}\" completed. Result:\n$it" } }
        } else {
            transformWithAnnotation(HttpRpcRequestBodyParameter::class.createInstance()).also {
                log.trace { "Transform body parameter  \"${parameter.name}\" without explicit annotation completed. Result:\n$it" }
            }
        }
    }

    private fun transformWithAnnotation(annotation: HttpRpcRequestBodyParameter) =
        EndpointParameter(
            id = parameter.name!!,
            name = annotation.name(parameter.name!!),
            description = annotation.description,
            required = annotation.required,
            nullable = parameter.type.isMarkedNullable,
            default = null,
            classType = parameter.type.jvmErasure.java,
            parameterizedTypes = parameter.getParameterizedTypes(),
            type = ParameterType.BODY
        )
}

private class BodyParametersExplicitTransformer(private val name: String, private val type: GenericParameterizedType) :
    ParametersTransformer {
    private companion object {
        private val log = contextLogger()
    }

    override fun transform(): EndpointParameter {
        log.trace("Transform explicit body parameter \"${name}\"")
        return with(HttpRpcRequestBodyParameter::class.createInstance()) {
            EndpointParameter(
                id = this@BodyParametersExplicitTransformer.name,
                name = this@BodyParametersExplicitTransformer.name,
                description = this.description,
                required = this.required,
                default = null,
                classType = type.clazz,
                parameterizedTypes = type.nestedParameterizedTypes,
                type = ParameterType.BODY
            )
        }.also { log.trace("Transform explicit body parameter \"$name\", result $it completed.") }
    }
}
