package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import picocli.CommandLine
import picocli.CommandLine.ParentCommand
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


@CommandLine.Command(name = "connect", description = ["Connects to Kafka broker to delete topics"])
class DeleteConnect : Runnable {

    @ParentCommand
    var delete: Delete? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for deletion to complete in seconds"]
    )
    var wait: Long = 30

    override fun run() {
        val client = Admin.create(delete!!.topic!!.getKafkaProperties())

        try {
            val topicNames = client.listTopics().names().get(wait, TimeUnit.SECONDS)
                .filter { it.startsWith(delete!!.topic!!.getHyphenatedNamePrefix()) }

            if (topicNames.isEmpty()) {
                println("No matching topics found")
            } else {
                println("Deleting topics: ${topicNames.joinToString()}")
                    client.deleteTopics(topicNames).all().get(wait, TimeUnit.SECONDS)
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    }

}
