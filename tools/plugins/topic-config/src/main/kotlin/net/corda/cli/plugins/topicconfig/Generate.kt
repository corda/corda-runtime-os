package net.corda.cli.plugins.topicconfig

import picocli.CommandLine
import java.io.FileDescriptor
import java.io.FileWriter

@CommandLine.Command(name = "generate", description = ["Generates a textual representation of the intended Kafka topic configuration"])
class Generate : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    override fun run() {
        create!!.mapper.writeValue(FileWriter(FileDescriptor.out), create!!.getGeneratedTopicConfigs())
    }
}