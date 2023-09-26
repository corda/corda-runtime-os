package net.corda.cli.plugins.topicconfig

import picocli.CommandLine

@CommandLine.Command(name = "generate", description = ["Generates a textual representation of the intended Kafka topic configuration"])
class Generate : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    override fun run() {
        val generatedTopicConfigs =  create!!.getGeneratedTopicConfigs()
        println(create!!.mapper.writeValueAsString(generatedTopicConfigs))
    }
}