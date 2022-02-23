package net.corda.introspiciere.cli

import picocli.CommandLine
import java.nio.ByteBuffer

@CommandLine.Command(name = "read")
class ReadCommand : BaseCommand() {

    @CommandLine.Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @CommandLine.Option(names = ["--key"], description = ["Message key"])
    private lateinit var key: String

    @CommandLine.Option(names = ["--schema"],
        required = true,
        description = ["Qualified name of the schema of the message"])
    private lateinit var schemaName: String

    override fun run() {
        val clazz = Class.forName(schemaName)
        val messages = httpClient.readMessages(topicName, key, schemaName)
        messages.forEach { msg ->
            val fromByteBuffer = clazz.getMethod("fromByteBuffer", ByteBuffer::class.java)
            val avro = fromByteBuffer.invoke(null, ByteBuffer.wrap(msg.schema))
            stdout.bufferedWriter().autoFlush { writer ->
                writer.appendLine(avro.toString())
            }
        }
    }
}