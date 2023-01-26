@file:Suppress("TooManyFunctions")
package net.corda.httprpc.server.impl.apigen.processing.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.tags.Tag
import java.io.InputStream
import java.util.Collections.singletonList
import java.util.Locale
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.server.impl.apigen.models.Endpoint
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelToOpenApiSchemaConverter
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.httprpc.tools.HttpPathUtils.toOpenApiPath
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.util.trace
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory

private val log =
    LoggerFactory.getLogger("net.corda.httprpc.server.impl.apigen.processing.openapi.ResourceToOpenApiSpecMapper.kt")

private const val MULTIPART_CONTENT_TYPE = "multipart/form-data"
private const val APPLICATION_JSON_CONTENT_TYPE = "application/json"

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
    val paths = Paths().apply { swaggerPathInfos.toSortedMap().forEach { addPathItem(it.key, it.value) } }
    val schemas =
        schemaModelContextHolder.getAllSchemas().map { it.key to SchemaModelToOpenApiSchemaConverter.convert(it.value) }
            .toMap().toSortedMap()
    return OpenAPI().apply {
        tags(tags.sortedBy { it.name })
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
            .`in`(type.name.lowercase())
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
        { endpointParam -> endpointParam.name.takeIf { it.isNotBlank() } ?: endpointParam.id },
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
        .content(Content().addMediaType(determineContentType(), this.toMediaType(schemaModelProvider, schemaName)))
        .also { log.trace { "Map ${this.size} EndpointParameters to RequestBody: $it completed." } }
}

private fun List<EndpointParameter>.toMediaType(
    schemaModelProvider: SchemaModelProvider,
    methodName: String
): MediaType {
    val isSingleRef = this.count() == 1 && schemaModelProvider.toSchemaModel(this.first()) is SchemaRefObjectModel
    val multiParams = this.count() > 1

    return if (this.isMultipartFileUpload()) {
        MediaType().schema(
            Schema<Any>().properties(this.toProperties(schemaModelProvider))
                .type(DataType.OBJECT.toString().lowercase())
        )
    } else if (isSingleRef) {
        MediaType().schema(
            SchemaModelToOpenApiSchemaConverter.convert(schemaModelProvider.toSchemaModel(this.first()))
        )
    } else if (multiParams) {
        MediaType().schema(
            SchemaModelToOpenApiSchemaConverter.convert(
                schemaModelProvider.toSchemaModel(this, methodName + "WrapperRequest")
            )
        )
    } else {
        MediaType().schema(
            Schema<Any>().properties(this.toProperties(schemaModelProvider))
                .type(DataType.OBJECT.toString().lowercase())
        )
    }
}

private fun List<EndpointParameter>.determineContentType() =
    if (this.isMultipartFileUpload()) {
        MULTIPART_CONTENT_TYPE
    } else {
        APPLICATION_JSON_CONTENT_TYPE
    }

private fun List<EndpointParameter>.isMultipartFileUpload(): Boolean {
    return this.any { endpointParameter ->
        endpointParameter.classType == InputStream::class.java ||
                endpointParameter.classType == HttpFileUpload::class.java ||
                endpointParameter.parameterizedTypes.any { it.clazz == HttpFileUpload::class.java }
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
                    ApiResponse().withResponseBodyFrom(this, schemaModelProvider)
                )
                .addApiResponse(HttpStatus.UNAUTHORIZED_401.toString(), ApiResponse().description("Unauthorized"))
                .addApiResponse(HttpStatus.FORBIDDEN_403.toString(), ApiResponse().description("Forbidden"))
        )
        .parameters(parameters.filter { it.type != ParameterType.BODY }
            .map { it.toOpenApiParameter(schemaModelProvider) })
        .requestBody(parameters.filter { it.type == ParameterType.BODY }
            .toRequestBody(schemaModelProvider, title.toValidSchemaName()))
        .also { log.trace { "Map Endpoint: \"$this\" to Operation: \"$it\" completed." } }
}

@VisibleForTesting
fun String.toValidMethodName() = lowercase().replace(Regex("\\W"), "_")

private fun String.toValidSchemaName(): String {
    return replaceFirstChar { ch ->
        if (ch.isLowerCase()) {
            ch.titlecase(Locale.getDefault())
        } else {
            ch.toString()
        }
    }.replace("\\W".toRegex(), "")
}

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
                    APPLICATION_JSON_CONTENT_TYPE,
                    MediaType().schema(createResponseSchema(schemaModelProvider, endpoint))
                )
            )
        } else this

        endpoint.responseBody.description.let {
            response.description = it.ifBlank { "Success" }
        }
        log.trace { "ApiResponse with ResponseBody from Endpoint: \"$endpoint\" completed." }
        return response
    } catch (e: Exception) {
        "Error in ApiResponse with ResponseBody from Endpoint: \"$endpoint\".".let {
            log.error("$it: ${e.message}")
            throw Exception(it, e)
        }
    }
}

private fun createResponseSchema(schemaModelProvider: SchemaModelProvider, endpoint: Endpoint): Schema<Any> {
    val schema = SchemaModelToOpenApiSchemaConverter.convert(
        schemaModelProvider.toSchemaModel(
            ParameterizedClass(
                endpoint.responseBody.type,
                endpoint.responseBody.parameterizedTypes,
                endpoint.responseBody.nullable
            )
        )
    )
    return wrapNullableReferencedTypeIfNecessary(endpoint, schema)
}

private fun wrapNullableReferencedTypeIfNecessary(endpoint: Endpoint, schema: Schema<Any>): Schema<Any> {
    // To make a referenced type nullable we must wrap it with an `allOf` property from `ComposedSchema` and set this as nullable.
    // We only need to perform wrapping if it is a referenced type.
    return if (endpoint.responseBody.nullable && schema.`$ref` != null) {
        ComposedSchema().apply {
            allOf = listOf(schema)
            nullable = true
        }
    } else {
        schema
    }
}

private fun Class<*>.isNull(): Boolean {
    log.trace { "Invoke isNull on class: ${this.name}" }
    return (this.isAssignableFrom(Void::class.java) || this.isAssignableFrom(Void.TYPE))
        .also { log.trace { "Invoke isNull on class: ${this.name} returned $it." } }
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
    return this.endpoints.groupBy { joinResourceAndEndpointPaths(path, it.path).toOpenApiPath() }.map {
        val getEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.GET == endpoint.method }
        val postEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.POST == endpoint.method }
        val putEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.PUT == endpoint.method }
        val deleteEndpoint = it.value.singleOrNull { endpoint -> EndpointMethod.DELETE == endpoint.method }
        val fullPath = it.key

        fullPath to PathItem().also { pathItem ->
            val tagName = singletonList(name)
            getEndpoint?.let {
                pathItem.get(getEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
            postEndpoint?.let {
                pathItem.post(postEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
            putEndpoint?.let {
                pathItem.put(putEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
            deleteEndpoint?.let {
                pathItem.delete(deleteEndpoint.toOperation(fullPath, schemaModelProvider).tags(tagName))
            }
        }
    }.toMap()
        .also { log.trace { "Map resource: ${this.name} to Map of Path to PathItem completed." } }
}



