package net.corda.httprpc.client.auth.credentials

interface CredentialsProvider {
    fun getCredentials(): Any
}
