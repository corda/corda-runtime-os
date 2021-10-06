package net.corda.httprpc.server.impl.security.provider.bearer.azuread

import com.nimbusds.jose.util.ResourceRetriever
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import java.net.URI
import java.net.URL

internal class AzureAdConfiguration private constructor(private val oidcProviderMetadata: OIDCProviderMetadata) {
    companion object {
        const val METADATA_PATH = "/.well-known/openid-configuration"

        fun fromIssuer(issuer: String, resourceRetriever: ResourceRetriever): AzureAdConfiguration {
            val metadataUrl = buildMetadataUrl(issuer)
            val metadataResource = resourceRetriever.retrieveResource(metadataUrl)
            return AzureAdConfiguration(OIDCProviderMetadata.parse(metadataResource.content))
        }

        private fun buildMetadataUrl(issuer: String): URL {
            val url = URL(issuer)
            return URL(url.protocol, url.host, url.port, url.path.trimEnd('/') + METADATA_PATH)
        }
    }

    val jwksUri: URI
        get() {
            return oidcProviderMetadata.jwkSetURI
        }
}
