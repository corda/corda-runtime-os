package net.corda.httprpc.client.auth.credentials

data class BearerTokenCredentialsProvider(val tokenProvider: BearerTokenProvider) : CredentialsProvider {
    override fun getCredentials(): Any {
        return BearerTokenCredentials(tokenProvider.token)
    }
}
