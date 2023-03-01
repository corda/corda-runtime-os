package net.corda.rest.client.auth.credentials

data class BasicAuthCredentials(val username: String, val password: String) : CredentialsProvider {
    override fun getCredentials(): Any {
        return this
    }
}
