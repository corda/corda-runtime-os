package net.corda.httprpc.server.impl.apigen.processing.openapi

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.config.RestServerSettingsProvider
import net.corda.httprpc.server.impl.internal.SwaggerUIRenderer
import net.corda.httprpc.server.impl.security.provider.bearer.azuread.AzureAdAuthenticationProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

/**
 * [OpenApiInfoProvider] is responsible for providing OpenAPI related values
 * from the given list of [Resource] and the [RestServerSettingsProvider].
 */
internal class OpenApiInfoProvider(
    private val resources: List<Resource>,
    private val configurationsProvider: RestServerSettingsProvider
) {

    internal companion object {
        internal fun String.jsonPath() = "$this.json"
        private val log = contextLogger()
    }

    val pathForOpenApiUI = "/${configurationsProvider.getBasePath()}/v${configurationsProvider.getApiVersion()}/swagger"
    val pathForOpenApiJson = pathForOpenApiUI.jsonPath()

    val swaggerUIRenderer = SwaggerUIRenderer(configurationsProvider)
    val openApiString: String = Json.pretty().writeValueAsString(generateOpenApi())

    private fun generateOpenApi(): OpenAPI {
        log.trace { "Generate OpenApi for ${resources.size} resources." }
        val basePath = configurationsProvider.getBasePath()
        val apiVersion = configurationsProvider.getApiVersion()
        return resources.toOpenAPI(SchemaModelContextHolder()).apply openapi@{
            info(createSwaggerInfo())
            addServersItem(Server().url("/$basePath/v${apiVersion}".replace("/+".toRegex(), "/")))
            components((components ?: Components()).apply {
                addSecuritySchemes(
                    "basicAuth",
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                )
                addSecurityItem(SecurityRequirement().addList("basicAuth"))
                addAzureAdIfNecessary(this@openapi, this)
            })
        }.also { log.trace { "Generate OpenApi for ${resources.size} resources completed." } }

    }

    private fun createSwaggerInfo() = Info().apply {
        log.trace { "Create SwaggerInfo." }
        version(configurationsProvider.getApiVersion())
        title(configurationsProvider.getApiTitle())
        description(configurationsProvider.getApiDescription())
        log.trace { "Create SwaggerInfo completed." }
    }

    private fun addAzureAdIfNecessary(openApi: OpenAPI, components: Components) {
        val azureAd = configurationsProvider.getSsoSettings()?.azureAd()
        if (azureAd != null) {
            components.addSecuritySchemes(
                "azuread", SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(
                        OAuthFlows()
                            .authorizationCode(OAuthFlow()
                                .authorizationUrl(azureAd.getAuthorizeUrl())
                                .tokenUrl(azureAd.getTokenUrl())
                                .scopes(Scopes().apply {
                                    AzureAdAuthenticationProvider.SCOPE.split(' ').forEach { scope ->
                                        addString(scope, scope)
                                    }
                                })
                            )
                    )
                    .extensions(mapOf("x-tokenName" to "id_token"))
            )


            openApi.addSecurityItem(SecurityRequirement().addList("azuread", "AzureAd authentication"))
        }
    }
}
