package net.corda.cli.plugins.topicconfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@CommandLine.Command(name = "delete", description = ["Generates deletion script for cleaning up topics"])
class Delete(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
) : Runnable {
    @CommandLine.Option(
        names = ["-f", "--prefix"],
        description = ["Set topic prefix for created topics"]
    )
    var topicPrefix: String? = null
    @CommandLine.Option(
        names = ["-a", "--address"],
        description = ["Bootstrap server address for topic create"],
        required = true
    )
    var bootstrapAddress: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["Directory to write all files to"]
    )
    var outputLocation: String? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private fun createFilter(): List<String> {
        return if (topicPrefix != null) {
            listOf("grep -ie '^$topicPrefix'")
        } else {
            emptyList()
        }
    }

    override fun run() {
        val outputPath = "/tmp/working_dir/output.txt"
        if (!Path.of(outputPath).parent.exists()) {
            Path.of(outputPath).parent.createDirectories()
        }
        val address = bootstrapAddress ?: throw NoValidBootstrapAddress(bootstrapAddress)
        val topicList = listOf(
            "cat $outputPath"
        )
        val topicFilter = createFilter()
        val topicDeletions = listOf(
            "while read -r topic; do kafka-topics.sh --command-config /tmp/working_dir/config.properties --bootstrap-server $address --delete --topic \"\$topic\"; done"
        )

        val operation = (topicList + topicFilter + topicDeletions).joinToString(" | ")

        val output =
            listOf(
                "kafka-topics.sh --command-config /tmp/working_dir/config.properties --bootstrap-server $address --list > $outputPath",
                // The first half of this tests that there is more than one line in the file (an "empty" file still contains a newline
                "c=\$(wc -l < $outputPath); if [ \$c -gt 1 ]; then $operation; fi"
            ).joinToString(System.lineSeparator())

        if (outputLocation != null) {
            logger.info("Wrote to path $outputLocation")
            val writer = writerFactory(outputLocation!!)
            writer.write(output)
            writer.flush()
            writer.close()
        } else {
            println(
                listOf(output)
            )
        }
    }
}
