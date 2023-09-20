package net.corda.cli.plugins.topicconfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(name = "generate", description = ["Generates a textual representation of the intended Kafka topic configuration"])
class Generate : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun run() {
        val generatedConfig = mutableMapOf<String, MutableList<Any>>()
        generatedConfig["topics"] = mutableListOf()
        generatedConfig["acls"] = mutableListOf()

        create!!.getTopicConfigs().map { topicConfig ->
            val topicName = create!!.getTopicName(topicConfig)

            logger.info("Generating configuration for topic $topicName")
            generatedConfig["topics"]!!.add(mapOf("name" to topicName, "config" to topicConfig.config))

            val acl = mutableMapOf<String, Any>()
            acl["topic"] = topicName

            val usersReadAccess = create!!.getUsersForProcessors(topicConfig.consumers)
            val usersWriteAccess = create!!.getUsersForProcessors(topicConfig.producers)

            acl["users"] = (usersReadAccess + usersWriteAccess).toSet().map {
                val operations = mutableListOf("describe")
                if (it in usersWriteAccess)
                    operations.add("write")
                if (it in usersReadAccess)
                    operations.add("read")
                mapOf("name" to it, "operations" to operations.reversed())
            }

            generatedConfig["acls"]!!.add(acl)
        }

        println(create!!.mapper.writeValueAsString(generatedConfig))
    }
}