package net.corda.rest.server.impl.internal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.rest.exception.BadRequestException
import java.io.InputStream
import net.corda.rest.server.impl.apigen.processing.Parameter
import net.corda.rest.server.impl.apigen.processing.ParameterType
import net.corda.rest.server.impl.apigen.processing.RouteInfo
import net.corda.rest.server.impl.utils.mapTo
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.util.function.Function

/**
 * Retrieve the Parameter(s) from the context
 */
internal interface ParameterRetriever : Function<ParametersRetrieverContext, Any?>

private fun String.decodeRawString(): String = URLDecoder.decode(this, "UTF-8")

internal object ParameterRetrieverFactory {
    fun create(parameter: Parameter, routeInfo: RouteInfo): ParameterRetriever =
        when (parameter.type) {
            ParameterType.PATH -> PathParameterRetriever(parameter)
            ParameterType.QUERY -> {
                if (parameter.classType == List::class.java) QueryParameterListRetriever(parameter)
                else QueryParameterRetriever(parameter)
            }
            ParameterType.BODY -> {
                if (routeInfo.isMultipartFileUpload) MultipartParameterRetriever(parameter)
                else BodyParameterRetriever(parameter, routeInfo)
            }
        }
}

@Suppress("TooGenericExceptionThrown")
private class PathParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to path parameter." }
            val rawParam = ctx.pathParam(parameter.name)
            val decodedParam = rawParam.decodeRawString()
            return decodedParam.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to path parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to path parameter".let {
                log.warn("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class QueryParameterListRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter list." }
            val paramValues = ctx.queryParams(parameter.name)

            if (parameter.required && paramValues.isEmpty())
                throw BadRequestException("Missing query parameter \"${parameter.name}\".")

            return paramValues.map { it.decodeRawString() }
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter list completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter list.".let {
                log.warn("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class QueryParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun apply(ctx: ParametersRetrieverContext): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter." }

            if (parameter.required && ctx.queryParam(parameter.name) == null)
                throw BadRequestException("Missing query parameter \"${parameter.name}\".")

            val rawQueryParam: String? = ctx.queryParam(parameter.name, parameter.default)
            return rawQueryParam?.decodeRawString()?.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter".let {
                log.warn("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class BodyParameterRetriever(private val parameter: Parameter, private val routeInfo: RouteInfo) : ParameterRetriever {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val RouteInfo.isSingleBodyParam: Boolean
        get() {
            return parameters.filter { it.type == ParameterType.BODY }.size == 1
        }

    override fun apply(ctx: ParametersRetrieverContext): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to body parameter." }

            // Outside of empty/null parameter there can be multiple options here:
            // Given Json:
            // { "prop1": "foo", "prop2": "bar"} it may correspond to a method:
            // doStuff(prop1: String, prop2: String)
            // or to a method:
            // doStuff(request: MyRequest), where MyRequest is `data class MyRequest(prop1: String, prop2: String)`

            val node = if (ctx.body().isBlank()) null else retrieveNodeFromBody(ctx)

            if (parameter.required && node == null) throw BadRequestException("Missing body parameter \"${parameter.name}\".")

            val field = node?.toString() ?: "null"
            return ctx.fromJsonString(field, parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to body parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to body parameter".let {
                log.warn("$it: ${e.message}")
                throw e
            }
        }
    }

    private fun retrieveNodeFromBody(ctx: ParametersRetrieverContext): JsonNode? {
        val rootNode = ctx.bodyAsClass(ObjectNode::class.java)
        return rootNode.get(parameter.name) ?: if (routeInfo.isSingleBodyParam) {
            rootNode
        } else null
    }
}

@Suppress("TooGenericExceptionThrown")
private class MultipartParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suppress("ComplexMethod")
    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to body parameter." }

            if (parameter.isFileUpload) {
                val uploadedFiles = ctx.uploadedFiles(parameter.name)

                if (uploadedFiles.isEmpty())
                    throw BadRequestException("Expected file with parameter name \"${parameter.name}\" but it was not found.")

                if (Collection::class.java.isAssignableFrom(parameter.classType))
                    return uploadedFiles

                if (InputStream::class.java.isAssignableFrom(parameter.classType)) {
                    return uploadedFiles.first().content
                }

                return uploadedFiles.first()
            }

            val formParameterAsList = ctx.formParams(parameter.name)

            if (!parameter.nullable && formParameterAsList.isEmpty()) {
                throw BadRequestException("Missing form parameter \"${parameter.name}\".")
            }

            log.trace { "Cast \"${parameter.name}\" to multipart form parameter completed." }

            if (Collection::class.java.isAssignableFrom(parameter.classType)) {
                return formParameterAsList
            }
            return formParameterAsList.first()
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to multipart form parameter".let {
                log.warn("$it: ${e.message}")
                throw e
            }
        }
    }
}