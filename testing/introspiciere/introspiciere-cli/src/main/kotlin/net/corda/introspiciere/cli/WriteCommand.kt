package net.corda.introspiciere.cli

import net.corda.introspiciere.domain.KafkaMessage
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory
import picocli.CommandLine
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer

@CommandLine.Command(name = "write")
class WriteCommand : BaseCommand() {

    object messages {
        const val unexpectedEndOfFileMessage = "Unexpected end of file. Double check the input."
        const val fileNotFoundMessage = "File %s not found"
        const val successMessage = "Message successfully sent to topic %s"
    }

    @CommandLine.Option(names = ["--topic"],
        required = true,
        description = ["Topic name"])
    private lateinit var topicName: String

    @CommandLine.Option(names = ["--key"],
        description = ["Message key"])
    private var key: String? = null

    @CommandLine.Option(names = ["--schema"],
        required = true,
        description = ["Qualified name of the schema of the message"])
    private lateinit var schemaName: String

    @CommandLine.Option(names = ["--file", "-f"],
        defaultValue = "-",
        description = ["Input file. Use '-' to read from stdin."])
    private lateinit var file: String

    @Suppress("UNUSED_VARIABLE")
    override fun run() {
        try {
            sendMessage()
            messages.successMessage.format(topicName).let(stdout::writeText)

        } catch (ex: EOFException) {
            messages.unexpectedEndOfFileMessage.let(stderr::writeText)

        } catch (ex: FileNotFoundException) {
            messages.fileNotFoundMessage.format(file).let(stderr::writeText)
        }
    }

    @Suppress("UNUSED_VARIABLE")
    private fun sendMessage() {
        val reader = if (file == "-") stdin else File(file).inputStream()
        val isClose = stdin.available()

        val schemaClass: Class<*> = Class.forName(schemaName)
        val record = readJson(reader, schemaClass)
        val buffer = encode(record, schemaClass)

        val kafkaMessage = KafkaMessage(
            topic = topicName,
            key = key,
            schema = buffer.toByteArray(),
            schemaClass = schemaName
        )

        httpClient.sendMessage(kafkaMessage)
    }

    private fun readJson(reader: InputStream, schemaClass: Class<*>): GenericData.Record {
        val getClassSchema = schemaClass.getMethod("getClassSchema")
        val schema = getClassSchema.invoke(null) as Schema
        val decoder = DecoderFactory().jsonDecoder(schema, reader)
        val datum = GenericDatumReader<GenericData.Record>(schema)
        return datum.read(null, decoder)
    }

    private fun encode(record: GenericData.Record, schemaClass: Class<*>): ByteBuffer {
        val getEncoder = schemaClass.getMethod("getEncoder")
        val encoder = getEncoder.invoke(null)
        val encode = encoder::class.java.methods.first { it.name == "encode" && it.parameterCount == 1 }
        return encode.invoke(encoder, record) as ByteBuffer
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }
}
