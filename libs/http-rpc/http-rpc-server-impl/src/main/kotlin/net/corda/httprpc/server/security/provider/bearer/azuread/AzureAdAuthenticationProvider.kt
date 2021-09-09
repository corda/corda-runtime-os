package net.corda.httprpc.server.security.provider.bearer.azuread

import com.nimbusds.jose.util.DefaultResourceRetriever
import net.corda.httprpc.server.security.RPCSecurityManager
import net.corda.httprpc.server.config.AzureAdSettingsProvider
import net.corda.httprpc.server.security.provider.bearer.oauth.JwtAuthenticationProvider
import net.corda.httprpc.server.security.provider.bearer.oauth.JwtProcessor
import net.corda.httprpc.server.security.provider.bearer.oauth.PriorityListJwtClaimExtractor
import net.corda.httprpc.server.security.provider.scheme.AuthenticationScheme
import net.corda.httprpc.server.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.httprpc.server.security.provider.scheme.AuthenticationSchemeProvider.Companion.REALM_KEY

internal class AzureAdAuthenticationProvider(
    private val settings: AzureAdSettingsProvider,
    jwtProcessor: JwtProcessor,
    rpcSecurityManager: RPCSecurityManager
) :
    JwtAuthenticationProvider(
        jwtProcessor,
        PriorityListJwtClaimExtractor(settings.getPrincipalClaimList()),
        rpcSecurityManager
    ), AuthenticationSchemeProvider {
    companion object {
        const val SCOPE_KEY = "scope"
        const val CLIENT_ID_KEY = "client_id"
        const val SCOPE = "openid profile email"
        private const val JWT_CONNECT_TIMEOUT_MS = 5000
        private const val JWT_READ_TIMEOUT_MS = 5000
        private const val JWT_SIZE_LIMIT = 512000

        fun createDefault(
            settings: AzureAdSettingsProvider,
            rpcSecurityManager: RPCSecurityManager
        ): AzureAdAuthenticationProvider {
            val issuers = AzureAdIssuersImpl(settings).apply {
                val additionalIssuers = settings.getTrustedIssuers()
                if (additionalIssuers?.isNotEmpty() == true) {
                    additionalIssuers.forEach { issuer ->
                        this.addTrustedIssuer(issuer)
                    }
                }
            }
            val resourceRetriever =
                DefaultResourceRetriever(JWT_CONNECT_TIMEOUT_MS, JWT_READ_TIMEOUT_MS, JWT_SIZE_LIMIT)
            val keySelector = AzureAdIssuerJWSKeySelector(issuers, resourceRetriever)
            val processor = AzureAdJwtProcessorImpl(settings, issuers, keySelector)

            return AzureAdAuthenticationProvider(settings, processor, rpcSecurityManager)
        }
    }

    override val authenticationMethod: AuthenticationScheme = AuthenticationScheme.BEARER

    override fun provideParameters(): Map<String, String> {
        return mapOf(
            REALM_KEY to settings.getAuthority(),
            SCOPE_KEY to SCOPE,
            CLIENT_ID_KEY to settings.getClientId()
        )
    }
}
