package net.corda.httprpc.ssl

interface SslCertReadServiceFactory {

    fun create() : SslCertReadService
}