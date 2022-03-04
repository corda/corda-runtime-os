package net.corda.introspiciere.cli

import net.corda.introspiciere.payloads.Msg
import net.corda.introspiciere.payloads.deserialize
import picocli.CommandLine
import picocli.CommandLine.Option
import java.time.Duration

@CommandLine.Command(name = "read")
class ReadCommand : BaseCommand() {

    companion object {
        internal fun exitCommandGracefullyInTesting() {
            continueLoop = false
        }

        private var continueLoop: Boolean = true
    }

    @Option(names = ["--topic"], required = true, description = ["Topic name"])
    private lateinit var topicName: String

    @Option(names = ["--key"], description = ["Message key"])
    private var key: String? = null

    @Option(names = ["--schema"],
        required = true,
        description = ["Qualified name of the schema of the message"]
    )
    private lateinit var schemaName: String

    @Option(
        names = ["--timeout"],
        defaultValue = "1000",
        description = ["Consumer poll timeout"]
    )
    private var timeoutInMillis: Long = 0

    @Option(names = ["--from-beginning"],
        description = ["Read messages from beginning"],
        defaultValue = "false"
    )
    private var fromBeginning: Boolean = false

    private val timeout by lazy { Duration.ofMillis(timeoutInMillis) }

    private val clazz by lazy { Class.forName(schemaName) }

    override fun run() {
        var batch =
            if (fromBeginning) httpClient.readFromBeginning(topicName, key, schemaName, timeout)
            else httpClient.readFromEnd(topicName, key, schemaName, timeout)
        print(batch.messages)

        continueLoop = true
        while (continueLoop) {
            batch = httpClient.readFrom(topicName, key, schemaName, batch.nextBatchTimestamp, timeout)
            if (batch.messages.isEmpty()) Thread.sleep(1000)
            else print(batch.messages)
        }
    }

    private fun print(messages: Iterable<Msg>) {
        messages.forEach { msg -> stdout.writeText(msg.deserialize(clazz).toString()) }
    }
}