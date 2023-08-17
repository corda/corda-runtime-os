package net.corda.cli.plugins.network.utils

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import java.time.Instant

class PrintUtils {
    companion object {
        val objectMapper = jacksonObjectMapper()
        inline fun <reified T> printJsonOutput(result: Any, output: T) {
            val module = SimpleModule()

            module.addSerializer(Instant::class.java, InstantSerializer.INSTANCE)
            module.addDeserializer(Instant::class.java, InstantDeserializer.INSTANT)

            objectMapper.registerModule(module)
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

            val pp = DefaultPrettyPrinter()
            pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

            val jsonString = objectMapper.writer(pp).writeValueAsString(result)
            val formattedString = jsonString
                .replace("\\n", "")
                .replace("\\","")
                .replace("\"\"", "")
            when (output) {
                is Output -> output.generateOutput(formattedString)
                else -> throw IllegalArgumentException("Unsupported output type")
            }
        }

        /**
         * This function [verifyAndPrintError] is present to address the issue
         * of the RemoteClient in rest-client automatically converts any non-200 codes into exceptions.
         * In this case, a 409 is converted into a ResourceAlreadyExistsException
         * with the payload of the body as the message of the exception.
         */
        fun verifyAndPrintError(action: () -> Unit) {
            try {
                action()
            } catch (e: Exception) {
                printJsonOutput(e.localizedMessage, ConsoleOutput())
            }
        }
    }
}