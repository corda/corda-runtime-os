package net.corda.rest.client.auth.credentials

data class BearerTokenCredentialsProvider(val tokenProvider: BearerTokenProvider) : CredentialsProvider {
    override fun getCredentials(): Any {
        return BearerTokenCredentials(tokenProvider.token)
    }
}
