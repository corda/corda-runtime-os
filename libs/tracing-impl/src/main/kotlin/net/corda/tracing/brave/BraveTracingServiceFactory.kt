package net.corda.tracing.brave

import aQute.bnd.annotation.spi.ServiceProvider
import net.corda.tracing.TracingService
import net.corda.tracing.TracingServiceFactory
import net.corda.v5.base.exceptions.CordaRuntimeException

@Suppress("unused")
@ServiceProvider(TracingServiceFactory::class)
class BraveTracingServiceFactory : TracingServiceFactory {
    override fun create(
        serviceName: String,
        zipkinHost: String?,
        samplesPerSecond: String?,
        extraTraceTags: Map<String, String>
    ): TracingService {
        val sampleRate = readSampleRateString(samplesPerSecond)
        return BraveTracingService(serviceName, zipkinHost, sampleRate, extraTraceTags)
    }

    private fun parseUnsignedIntWithErrorHandling(string: String) = try {
        Integer.parseUnsignedInt(string)
    } catch (e: NumberFormatException) {
        throw CordaRuntimeException("Invalid --trace-samples-per-second, failed to parse \"$string\" as unsigned int", e)
    }

    private fun readSampleRateString(samplesPerSecond: String?): SampleRate = when {
        samplesPerSecond.isNullOrEmpty() -> PerSecond(1)
        samplesPerSecond.lowercase() == "unlimited" -> Unlimited
        else -> PerSecond(parseUnsignedIntWithErrorHandling(samplesPerSecond))
    }
}
