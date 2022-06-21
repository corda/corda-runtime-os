package net.corda.cli.plugins.topicconfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@CommandLine.Command(name = "script", description = ["Generates a script for the deletion of Kafka topics"])
class DeleteScript(
    private val writerFactory: (String) -> FileWriter = { file -> FileWriter(File(file)) },
) : Runnable {

    @CommandLine.ParentCommand
    var delete: Delete? = null

    @CommandLine.Option(
        names = ["-f", "--file"],
        description = ["File to write deletion script to"]
    )
    var file: String? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private fun createFilter(): List<String> {
        return if (delete!!.topic!!.namePrefix != null) {
            listOf("grep -e '^${delete!!.topic!!.getHyphenatedNamePrefix()}'")
        } else {
            emptyList()
        }
    }

    override fun run() {
        val outputPath = "/tmp/working_dir/output.txt"
        if (!Path.of(outputPath).parent.exists()) {
            Path.of(outputPath).parent.createDirectories()
        }

        val topicList = listOf(
            "cat $outputPath"
        )
        val topicFilter = createFilter()
        @Suppress("MaxLineLength")
        val topicDeletions = listOf(
            "while read -r topic; do ${delete!!.topic!!.getKafkaTopicsCommand()} --delete --topic \"\$topic\"; done"
        )

        val operation = (topicList + topicFilter + topicDeletions).joinToString(" | ")

        val output =
            listOf(
                "${delete!!.topic!!.getKafkaTopicsCommand()} --list > $outputPath",
                // The first half of this tests that there is more than one line in the file (an "empty" file still contains a newline
                "c=\$(wc -l < $outputPath); if [ \$c -gt 1 ]; then $operation; fi"
            ).joinToString(System.lineSeparator())

        if (file != null) {
            val writer = writerFactory(file!!)
            writer.write(output)
            writer.flush()
            writer.close()
            logger.info("Wrote to path $file")
        } else {
            println(
                listOf(output)
            )
        }
    }
}
