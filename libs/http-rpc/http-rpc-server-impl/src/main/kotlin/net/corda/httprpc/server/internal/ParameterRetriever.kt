package net.corda.httprpc.server.internal

import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJson
import net.corda.httprpc.server.apigen.processing.Parameter
import net.corda.httprpc.server.apigen.processing.ParameterType
import net.corda.httprpc.server.exception.MissingParameterException
import net.corda.httprpc.server.utils.mapTo
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import java.net.URLDecoder

internal interface ParameterRetriever {
    /**
     * Retrieve the Parameter(s) from the context
     * @return The retrieved Parameter value(s) cast to the classType of the Parameter
     */
    fun get(ctx: Context): Any?
}

private fun String.decodeRawString(): String = URLDecoder.decode(this, "UTF-8")

internal object ParameterRetrieverFactory {
    fun create(parameter: Parameter) =
        when (parameter.type) {
            ParameterType.PATH -> PathParameterRetriever(parameter)
            ParameterType.QUERY -> if (parameter.classType == List::class.java) QueryParameterListRetriever(parameter)
            else QueryParameterRetriever(parameter)
            ParameterType.BODY -> BodyParameterRetriever(parameter)
        }
}

@Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
private class PathParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun get(ctx: Context): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to path parameter." }
            val rawParam = ctx.pathParam(parameter.name)
            val decodedParam = rawParam.decodeRawString()
            return decodedParam.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to path parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to path parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
private class QueryParameterListRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun get(ctx: Context): List<String> {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter list." }
            val paramValues = ctx.queryParams(parameter.name)

            if (parameter.required && paramValues.isEmpty())
                throw MissingParameterException("Missing query parameter \"${parameter.name}\".")

            return paramValues.map { it.decodeRawString() }
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter list completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter list.".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
private class QueryParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun get(ctx: Context): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter." }

            if (parameter.required && ctx.queryParam(parameter.name) == null)
                throw MissingParameterException("Missing query parameter \"${parameter.name}\".")

            val rawQueryParam: String? = ctx.queryParam(parameter.name, parameter.default)
            return rawQueryParam?.decodeRawString()?.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")
private class BodyParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun get(ctx: Context): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to body parameter." }

            val node = if (ctx.body().isBlank()) null else ctx.bodyAsClass(ObjectNode::class.java).get(parameter.name)

            if (parameter.required && node == null) throw MissingParameterException("Missing body parameter \"${parameter.name}\".")

            val field = node?.toString() ?: "null"
            return JavalinJson.fromJson(field, parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to body parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to body parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}
