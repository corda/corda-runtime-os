package net.corda.cli.plugins.topicconfig

import org.apache.kafka.clients.admin.Admin
import picocli.CommandLine
import picocli.CommandLine.ParentCommand
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@CommandLine.Command(name = "connect", description = ["Connects to Kafka broker to delete topics"])
class DeleteConnect : Runnable {

    @ParentCommand
    var delete: Delete? = null

    @CommandLine.Option(
        names = ["-w", "--wait"],
        description = ["Time to wait for deletion to complete in seconds"]
    )
    var wait: Long = 60

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val client = Admin.create(delete!!.topic!!.getKafkaProperties())

        try {
            val topicNames = client.existingTopicNamesWithPrefix(delete!!.topic!!.namePrefix, wait)

            if (topicNames.isEmpty()) {
                println("No matching topics found")
            } else {
                println("Deleting topics: ${topicNames.joinToString()}")
                client.deleteTopics(topicNames).all().get(wait, TimeUnit.SECONDS)
                client.waitForTopicDeletion(delete!!.topic!!.namePrefix, wait)
            }
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

        Thread.currentThread().contextClassLoader = contextCL
    }

}

fun Admin.waitForTopicDeletion(prefix: String, wait: Long) {
    val end = LocalDateTime.now().plusSeconds(wait)
    while (true) {
        val existingTopicNames = existingTopicNamesWithPrefix(prefix, wait)
        if (existingTopicNames.isEmpty()) {
            break
        } else {
            if (LocalDateTime.now().isAfter(end)) {
                throw TimeoutException("Timed out deleting topics: ${existingTopicNames.joinToString()}")
            }
            Thread.sleep(1000)
        }
    }
}

fun Admin.existingTopicNamesWithPrefix(prefix: String, wait: Long) =
    listTopics().names().get(wait, TimeUnit.SECONDS)
        .filter { it.startsWith(prefix) }
