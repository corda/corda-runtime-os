package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.rest.json.serialization.jacksonObjectMapper
import net.corda.sdk.network.GenerateStaticGroupPolicy
import java.io.File
import java.time.Instant

class GroupPolicyHelper {
    companion object {
        private val objectMapper = jacksonObjectMapper().apply {
            val module = SimpleModule().apply {
                addSerializer(Instant::class.java, InstantSerializer.INSTANCE)
                addDeserializer(Instant::class.java, InstantDeserializer.INSTANT)
            }

            registerModule(module)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }

        private val prettyPrintWriter = DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
        }

        private val groupPolicyGenerator = GenerateStaticGroupPolicy()

        private const val ENDPOINT_URL = "http://localhost:1080"
        private const val ENDPOINT_PROTOCOL = 1
    }

    fun createStaticGroupPolicy(
        targetPolicyFile: File,
        x500Names: List<String?>,
    ) {
        try {
            groupPolicyGenerator.createMembersListFromListOfX500Strings(
                x500Names.filterNotNull(),
                ENDPOINT_URL,
                ENDPOINT_PROTOCOL,
            ).let { members ->
                groupPolicyGenerator.generateStaticGroupPolicy(members)
            }.let {
                objectMapper
                    .writer(prettyPrintWriter)
                    .writeValue(targetPolicyFile, it)
            }
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Unable to create group policy: ${e.message}", e)
        }
    }
}