package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import net.corda.rest.json.serialization.jacksonObjectMapper
import java.io.File
import java.time.Instant

internal object PrintHelper {
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

    fun writeGroupPolicyToFile(file: File, groupPolicy: Any) {
        objectMapper
            .writer(prettyPrintWriter)
            .writeValue(file, groupPolicy)
    }
}