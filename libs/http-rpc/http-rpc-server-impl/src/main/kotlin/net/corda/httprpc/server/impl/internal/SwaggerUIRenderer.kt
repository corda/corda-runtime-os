package net.corda.httprpc.server.impl.internal

import io.javalin.core.util.Util
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.InternalServerErrorResponse
import net.corda.v5.base.util.contextLogger
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider.Companion.jsonPath
import net.corda.httprpc.server.config.RestServerSettingsProvider
import org.osgi.framework.FrameworkUtil

/**
 * [SwaggerUIRenderer] is responsible for rendering the swagger html.
 *
 */
internal class SwaggerUIRenderer(private val configurationProvider: RestServerSettingsProvider) : Handler {

    private companion object {
        private val log = contextLogger()
    }

    override fun handle(ctx: Context) {
        val swaggerUiVersion = OptionalDependency.SWAGGERUI.version

        val bundle = FrameworkUtil.getBundle(SwaggerUIRenderer::class.java)
        if (bundle == null) {
            // This branch is used by non-OSGi tests.
            if (Util.getResourceUrl("META-INF/resources/webjars/swagger-ui/$swaggerUiVersion/swagger-ui.css") == null) {
                "Missing dependency '${OptionalDependency.SWAGGERUI.displayName}'".apply {
                    log.error(this)
                    throw InternalServerErrorResponse(this)
                }
            }
        } else {
            if (bundle.bundleContext.bundles.find { it.symbolicName == OptionalDependency.SWAGGERUI.symbolicName } == null) {
                "Missing dependency '${OptionalDependency.SWAGGERUI.displayName}'".apply {
                    log.error(this)
                    throw InternalServerErrorResponse(this)
                }
            }
        }
        @Suppress("MaxLineLength")
        ctx.html(
            """
            <head>
                <meta charset="UTF-8">
                <title>Swagger UI</title>
                <link rel="icon" type="image/png" 
                href="${ctx.contextPath()}/webjars/swagger-ui/$swaggerUiVersion/favicon-16x16.png" sizes="16x16" />
                <link rel="stylesheet" href="${ctx.contextPath()}/webjars/swagger-ui/$swaggerUiVersion/swagger-ui.css" >
                <script src="${ctx.contextPath()}/webjars/swagger-ui/$swaggerUiVersion/swagger-ui-bundle.js"></script>
                <style>body{background:#fafafa;}</style>
            </head>
            <body>
                <div id="swagger-ui"></div>
                <script>
                    if (!window.isOpenReplaced) {
                        window.open = function (open) {
                            return function (url) {
                                url = url
                                    // Swagger UI does not support custom response_type parameters. 
                                    //gAzure Active Directory requires an 'id_token' value to
                                    // be passed instead of 'token' (See https://github.com/swagger-api/swagger-ui/issues/1974).
                                    .replace('response_type=token', 'response_type=id_token');
                                return open.call(window, url);
                            };
                        } (window.open);
                        
                        window.isOpenReplaced = true;
                    }
                
                    const ui = SwaggerUIBundle({
                        url: "${ctx.path().jsonPath()}",
                        dom_id: "#swagger-ui",
                        deepLinking: true,
                        presets: [SwaggerUIBundle.presets.apis],
                        oauth2RedirectUrl: `${"$"}{window.location.protocol}//${"$"}{window.location.host}${ctx.contextPath()}/webjars/swagger-ui/$swaggerUiVersion/oauth2-redirect.html`,
                        onComplete: function() {
                            ${getInitOAuth()}
                        }
                    });
                </script>
            </body>""".trimIndent()
        )
    }

    private fun getInitOAuth(): String {
        val sso = configurationProvider.getSsoSettings()?.azureAd()
        return if (sso != null) {
            """
                ui.initOAuth({
                        clientId: "${sso.getClientId()}",
                        clientSecret: "${sso.getClientSecret() ?: ""}",
                        scopes: ["openid", "profile", "email"],
                        usePkceWithAuthorizationCodeGrant: true
                      })
            """.trimIndent()
        } else {
            ""
        }
    }
}
