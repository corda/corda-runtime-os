package net.corda.cli.commands.topicconfig

import picocli.CommandLine
import java.io.FileDescriptor
import java.io.FileWriter

@CommandLine.Command(name = "preview",
    description = ["Generates a textual representation of the intended Kafka topic configuration"],
    mixinStandardHelpOptions = true)
class Preview : Runnable {

    @CommandLine.ParentCommand
    var create: Create? = null

    override fun run() {
        create!!.topicConfigCreator.mapper.writeValue(FileWriter(FileDescriptor.out), create!!.getTopicConfigsForPreview())
    }
}