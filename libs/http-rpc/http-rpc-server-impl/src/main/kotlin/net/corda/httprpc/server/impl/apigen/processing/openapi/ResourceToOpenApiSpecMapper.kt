package net.corda.httprpc.server.impl.apigen.processing.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.tags.Tag
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelToOpenApiSchemaConverter
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.trace
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import java.util.Collections.singletonList

private val log =
    LoggerFactory.getLogger("net.corda.httprpc.server.impl.apigen.processing.openapi.ResourceToOpenApiSpecMapper.kt")

/**
 * Convert a Resource list to an OpenAPI object
 */
internal fun List<Resource>.toOpenAPI(schemaModelContextHolder: SchemaModelContextHolder): OpenAPI {


    log.trace { "Map \"${this.size}\" resources to OpenAPI." }
    val swaggerPathInfos = mutableMapOf<String, PathItem>()
    val tags = mutableListOf<Tag>()

    this.forEach {
        swaggerPathInfos.putAll(it.getPathToPathItems(DefaultSchemaModelProvider(schemaModelContextHolder)))
        tags.add(it.toTag())
    }
    val paths = Paths().apply { swaggerPathInfos.forEach { addPathItem(it.key, it.value) } }
    val schemas =
        schemaModelContextHolder.getAllSchemas().map { it.key to SchemaModelToOpenApiSchemaConverter.convert(it.value) }
            .toMap()
    return OpenAPI().apply {
        tags(tags)
        paths(paths)

        components(
            Components().apply {
                schemas(schemas)
            }
        )
    }.also { log.trace { "Map \"${this.size}\" resources to OpenAPI completed." } }
}

@Suppress("TooGenericExceptionThrown")
@VisibleForTesting
internal fun EndpointParameter.toOpenApiParameter(schemaModelProvider: SchemaModelProvider): Parameter {
    try {
        log.trace { "Map EndpointParameter: \"$this\" to OpenApi Parameter." }
        return Parameter()
            .name(name.takeIf { it.isNotBlank() } ?: id)
            .description(description)
            .required(required)
            .schema(
                    SchemaModelToOpenApiSchemaConverter.convert(schemaModelProvider.toSchemaModel(this)
                )
            )
            .`in`(type.name.toLowerCase())
            .also { log.trace { "Map EndpointParameter: \"$this\" to OpenApi Parameter: $it completed." } }
    } catch (e: Exception) {
        "Error when mapping EndpointParameter: \"$this\" to OpenApi Parameter.".let {
            log.error("$it: ${e.message}")
            throw Exception(it, e)
        }
    }
}

private fun List<EndpointParameter>.toProperties(schemaModelProvider: SchemaModelProvider): Map<String, Schema<Any>> {
    log.trace { "Map \"${this.size}\" EndpointParameters to Schema properties." }
    return this.associateBy(
        { it.id },
        {
            SchemaModelToOpenApiSchemaConverter.convert(
                schemaModelProvider.toSchemaModel(it)
            )
        }
    ).also { log.trace { "Map \"${this.size}\" EndpointParameters to Schema properties completed." } }
}

internal fun List<EndpointParameter>.toRequestBody(
    schemaModelProvider: SchemaModelProvider,
    schemaName: String
): RequestBody? {
    log.trace { "Map ${this.size} EndpointParameters to RequestBody." }
    if (this.isEmpty()) return null
    return RequestBody()
        .description("requestBody")
        .required(this.any { it.required })
        .content(Content().addMediaType("application/json", this.toMediaType(schemaModelProvider, schemaName)))
        .also { log.trace { "Map ${this.size} EndpointParameters to RequestBody: $it completed." } }
}

private fun List<EndpointParameter>.toMediaType(
    schemaModelProvider: SchemaModelProvider,
    methodName: String
): MediaType {
    val isSingleRef = this.count() == 1 && schemaModelProvider.toSchemaModel(this.first()) is SchemaRefObjectModel
    val multiParams = this.count() > 1

    return if (isSingleRef || multiParams) {
        MediaType().schema(
            SchemaModelToOpenApiSchemaConverter.convert(
                schemaModelProvider.toSchemaModel(this, methodName + "Request")
            )
        )
    } else {
        MediaType().schema(
            Schema<Any>().properties(this.toProperties(schemaModelProvider))
                .type(DataType.OBJECT.toString().toLowerCase())
        )
    }
}

@VisibleForTesting
internal fun Endpoint.toOperation(path: String, schemaModelProvider: SchemaModelProvider): Operation {
    log.trace { "Map Endpoint: \"$this\" to Operation." }
    return Operation()
        .operationId("${method}$path".toValidMethodName()) //Swagger will use this as the method name when generating the client
        .description(description)
        .responses(
            ApiResponses()
                .addApiResponse(
                    HttpStatus.OK_200.toString(),
                    ApiResponse().description("Success.").withResponseBodyFrom(this, schemaModelProvider)
                )
                .addApiResponse(HttpStatus.UNAUTHORIZED_401.toString(), ApiResponse().description("Unauthorized."))
                .addApiResponse(HttpStatus.FORBIDDEN_403.toString(), ApiResponse().description("Forbidden."))
        )
        .parameters(parameters.filter { it.type != ParameterType.BODY }
            .map { it.toOpenApiParameter(schemaModelProvider) })
        .requestBody(parameters.filter { it.type == ParameterType.BODY }
            .toRequestBody(schemaModelProvider, title.toValidSchemaName()))
        .also { log.trace { "Map Endpoint: \"$this\" to Operation: \"$it\" completed." } }
}

@VisibleForTesting
fun String.toValidMethodName() = toLowerCase().replace(Regex("\\W"), "_")

private fun String.toValidSchemaName() = capitalize().replace(Regex("\\W"), "")

@Suppress("TooGenericExceptionThrown")
private fun ApiResponse.withResponseBodyFrom(
    endpoint: Endpoint,
    schemaModelProvider: SchemaModelProvider
): ApiResponse {
    try {
        log.trace { "ApiResponse with ResponseBody from Endpoint: \"$endpoint\"." }
        val response = if (!endpoint.responseBody.type.isNull()) {
            this.content(
                Content().addMediaType(
                    "application/json",
                    MediaType().schema(
                        SchemaModelToOpenApiSchemaConverter.convert(
                            schemaModelProvider.toSchemaModel(
                                ParameterizedClass(
                                    endpoint.responseBody.type,
                                    endpoint.responseBody.parameterizedTypes
                                )
                            )
                        )
                    )
                )
            )
        } else this
        log.trace { "ApiResponse with ResponseBody from Endpoint: \"$endpoint\" completed." }
        return response
    } catch (e: Exception) {
        "Error in ApiResponse with ResponseBody from Endpoint: \"$endpoint\".".let {
            log.error("$it: ${e.message}")
            throw Exception(it, e)
        }
    }
}

private fun Class<*>.isNull(): Boolean {
    log.trace { "Invoke isNull on class: ${this.name}" }
    return (this.isAssignableFrom(Void::class.java) || this.isAssignableFrom(Void.TYPE))
        .also { log.trace { "Invoke isNull on class: ${this.name} returned $it." } }
}

/**
 * Swagger requires that all path names start with `/`.
 */

@VisibleForTesting
internal fun toOpenApiPath(resourcePath: String, endPointPath: String): String {
    log.trace { "Map resourcePath: \"$resourcePath\" and endPointPath: \"$endPointPath\" to OpenApi path." }
    return "/$resourcePath/$endPointPath".replace("/+".toRegex(), "/")
        .also { log.trace { "Map resourcePath: \"$resourcePath\" and endPointPath: \"endPointPath\" to OpenApi path: \"$it\" completed." } }
}

private fun Resource.toTag(): Tag {
    log.trace { "Map resource: ${this.name} to OpenApi Tag." }
    return Tag()
        .name(name)
        .description(description)
        .also { log.trace { "Map resource: \"${this.name}\" to OpenApi Tag: \"$it\" completed." } }
}

private fun Resource.getPathToPathItems(schemaModelProvider: SchemaModelProvider): Map<String, PathItem> {
    log.trace { "Map resource: \"${this.name}\" to Map of Path to PathItem." }
    return this.endpoints.groupBy { toOpenApiPath(path, it.path) }.map {
        val getEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.GET == endpoint.method }
        val postEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.POST == endpoint.method }
        val fullPath = it.key

        fullPath to PathItem().also { pathItem ->
            val tagName = singletonList(name)
            getEndpoint?.let {
                pathItem.get(getEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
            postEndpoint?.let {
                pathItem.post(postEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
        }
    }.toMap()
        .also { log.trace { "Map resource: ${this.name} to Map of Path to PathItem completed." } }
}



