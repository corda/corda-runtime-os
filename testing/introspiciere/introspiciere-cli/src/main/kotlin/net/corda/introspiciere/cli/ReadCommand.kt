package net.corda.introspiciere.cli

import picocli.CommandLine
import java.nio.ByteBuffer

@CommandLine.Command(name = "read")
class ReadCommand : BaseCommand() {

    companion object {
        internal fun exitCommandGracefullyInTesting() {
            continueLoop = false
        }
        private var continueLoop: Boolean = true
    }

    @CommandLine.Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @CommandLine.Option(names = ["--key"], description = ["Message key"])
    private lateinit var key: String

    @CommandLine.Option(names = ["--schema"],
        required = true,
        description = ["Qualified name of the schema of the message"]
    )
    private lateinit var schemaName: String

    @CommandLine.Option(names = ["--from-beginning"],
        description = ["Read messages from beginning"],
        defaultValue = "false"
    )
    private var fromBeginning: Boolean = false

    override fun run() {
//        val clazz = Class.forName(schemaName)
//
//        var latestOffsets =
//            if (fromBeginning) httpClient.beginningOffsets(topicName, schemaName)
//            else httpClient.endOffsets(topicName, schemaName)
//
//        continueLoop = true
//        while (continueLoop) {
//            val batch = httpClient.readMessages(topicName, key, schemaName, latestOffsets)
//
//            for (message in batch.messages) {
//                val fromByteBuffer = clazz.getMethod("fromByteBuffer", ByteBuffer::class.java)
//                val avro = fromByteBuffer.invoke(null, ByteBuffer.wrap(message))
//                stdout.bufferedWriter().autoFlush { it.appendLine(avro.toString()) }
//            }
//
//            latestOffsets = batch.latestOffsets
//        }
    }
}