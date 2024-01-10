package net.corda.rest.ssl

interface SslCertReadServiceFactory {

    fun create() : SslCertReadService
}