package net.corda.rest.client.auth.credentials

interface CredentialsProvider {
    fun getCredentials(): Any
}
