package net.corda.crypto.tck

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ExecutionOptionsExtension : ParameterResolver {
    companion object {
        private const val CONFIGURATION_PARAMETER_KEY = "tck.executionOptions"

        private val map = ConcurrentHashMap<String, ExecutionOptions>()

        fun register(requestBuilder: LauncherDiscoveryRequestBuilder, options: ExecutionOptions) {
            val id = UUID.randomUUID().toString()
            map[id] = options
            requestBuilder.configurationParameter(CONFIGURATION_PARAMETER_KEY, id)
        }

        fun unregister(options: ExecutionOptions) {
            val found = map.filterValues { it == options }.entries
            found.forEach {
                map.remove(it.key)
            }
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
        parameterContext.parameter.type.isAssignableFrom(ExecutionOptions::class.java)

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        val id = extensionContext.getConfigurationParameter(CONFIGURATION_PARAMETER_KEY)
        return map.getValue(id.get())
    }
}