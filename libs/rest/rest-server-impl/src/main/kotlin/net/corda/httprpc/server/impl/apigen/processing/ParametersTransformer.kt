package net.corda.httprpc.server.impl.apigen.processing

import java.io.InputStream
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory
import net.corda.v5.base.util.trace
import net.corda.httprpc.annotations.isRestParameterAnnotation
import net.corda.httprpc.tools.annotations.extensions.name
import java.lang.reflect.Parameter
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.jvmErasure
import net.corda.httprpc.HttpFileUpload

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
        param.annotations.singleOrNull { it.isRestParameterAnnotation() }.let {
            when (it) {
                is RestPathParameter -> PathParametersTransformer(param, it)
                is RestQueryParameter -> QueryParametersTransformer(param, it)
                is ClientRequestBodyParameter -> BodyParametersTransformer(param, it)
                else -> BodyParametersTransformer(param, null)
            }
        }
}

private class PathParametersTransformer(
    private val parameter: KParameter,
    private val annotation: RestPathParameter
) : ParametersTransformer {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
    private val annotation: RestQueryParameter
) : ParametersTransformer {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
    private val annotation: ClientRequestBodyParameter?
) : ParametersTransformer {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun transform(): EndpointParameter {
        log.trace { "Transform body parameter \"${parameter.name}\"" }
        return if (annotation != null) {
            transformWithAnnotation(annotation).also {
                log.trace { "Transform body parameter \"${parameter.name}\" completed. Result:\n$it" }
            }
        } else {
            transformWithAnnotation(ClientRequestBodyParameter::class.createInstance()).also {
                log.trace { "Transform body parameter  \"${parameter.name}\" without explicit annotation completed. Result:\n$it" }
            }
        }
    }

    private fun transformWithAnnotation(annotation: ClientRequestBodyParameter): EndpointParameter {
        val classType = parameter.type.jvmErasure.java
        val parameterizedTypes = parameter.getParameterizedTypes()
        return EndpointParameter(
            id = parameter.name!!,
            name = annotation.name(parameter.name!!),
            description = annotation.description,
            required = annotation.required,
            nullable = parameter.type.isMarkedNullable,
            default = null,
            classType = classType,
            parameterizedTypes = parameterizedTypes,
            type = ParameterType.BODY,
            isFile = determineIfParameterIsFile(classType, parameterizedTypes)
        )
    }
}

private class BodyParametersExplicitTransformer(private val name: String, private val type: GenericParameterizedType) :
    ParametersTransformer {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun transform(): EndpointParameter {
        log.trace("Transform explicit body parameter \"${name}\"")
        return with(ClientRequestBodyParameter::class.createInstance()) {
            EndpointParameter(
                id = this@BodyParametersExplicitTransformer.name,
                name = this@BodyParametersExplicitTransformer.name,
                description = this.description,
                required = this.required,
                default = null,
                classType = type.clazz,
                parameterizedTypes = type.nestedParameterizedTypes,
                type = ParameterType.BODY,
                isFile = determineIfParameterIsFile(type.clazz, type.nestedParameterizedTypes)
            )
        }.also { log.trace("Transform explicit body parameter \"$name\", result $it completed.") }
    }
}

private fun determineIfParameterIsFile(classType: Class<out Any>, parameterizedTypes: List<GenericParameterizedType>): Boolean {
    return classType == InputStream::class.java ||
            classType == HttpFileUpload::class.java ||
            parameterizedTypes.any { it.clazz == HttpFileUpload::class.java }
}