package net.corda.cli.commands.network.utils

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import java.time.Instant

object PrintUtils {
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

    fun printJsonOutput(result: Any, output: Output) {
        output.generateOutput(
            objectMapper
                .writer(prettyPrintWriter)
                .writeValueAsString(result),
        )
    }

    fun verifyAndPrintError(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            /**
             * This is present to address the issue of the RemoteClient in
             * rest-client automatically converting any non-200 codes into exceptions with the response body as message.
             */
            printJsonOutput(e.localizedMessage, ConsoleOutput())
        }
    }
}
