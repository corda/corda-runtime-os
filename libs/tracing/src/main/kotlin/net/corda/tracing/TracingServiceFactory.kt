package net.corda.tracing

interface TracingServiceFactory {
    fun create(serviceName: String, zipkinHost: String?, samplesPerSecond: String?): TracingService
}
