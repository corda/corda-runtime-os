package net.corda.rest.server.config.models

data class AzureAdSettings(
    val clientId: String,
    val clientSecret: String?,
    val tenantId: String,
    val principalNameClaims: List<String> = DEFAULT_CLAIMS,
    val appIdUri: String? = null,
    val trustedIssuers: List<String>? = null
) {
    companion object {
        val DEFAULT_CLAIMS = listOf("upn", "preferred_username", "email", "appid", "azp")
    }

    override fun toString() = "AzureAdSettings(clientId=$clientId, tenantId=$tenantId, " +
            "principalNameClaims=$principalNameClaims, appIdUri=$appIdUri, trustedIssuers=$trustedIssuers)"

}
