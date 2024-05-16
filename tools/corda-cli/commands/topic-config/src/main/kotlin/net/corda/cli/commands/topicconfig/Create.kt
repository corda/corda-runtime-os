package net.corda.cli.commands.topicconfig

import net.corda.sdk.bootstrap.topicconfig.TopicConfigCreator
import picocli.CommandLine

@CommandLine.Command(
    name = "create",
    description = ["Create Kafka topics"],
    subcommands = [Preview::class, CreateConnect::class],
    mixinStandardHelpOptions = true
)
class Create(
    cl: ClassLoader = TopicConfigCommand.classLoader
) {

    @CommandLine.ParentCommand
    var topic: TopicConfigCommand? = null

    @CommandLine.Option(
        names = ["-r", "--replicas"],
        description = ["Override replica count globally"]
    )
    var replicaOverride: Short = 1

    @CommandLine.Option(
        names = ["-p", "--partitions"],
        description = ["Override partition count globally"]
    )
    var partitionOverride: Int = 1

    @CommandLine.Option(
        names = ["-o", "--overrides"],
        description = ["Relative path of override Kafka topic configuration file in YAML format"]
    )
    var overrideFilePath: String? = null

    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["One or more Corda workers and their respective Kafka users e.g. -u crypto=Charlie -u rest=Rob"]
    )
    var kafkaUsers: Map<String, String> = emptyMap()

    val topicConfigCreator = TopicConfigCreator(classLoader = cl)

    fun getTopicConfigsForPreview(): TopicConfigCreator.PreviewTopicConfigurations {
        return topicConfigCreator.applyOverrides(
            config = topicConfigCreator.getTopicConfigsForPreview(
                topicConfigurations = topicConfigCreator.getTopicConfigs(),
                topicPrefix = topic!!.namePrefix,
                partitionOverride = partitionOverride,
                replicaOverride = replicaOverride,
                kafkaUsers = kafkaUsers
            ),
            topicPrefix = topic!!.namePrefix,
            overrideFilePath = overrideFilePath
        )
    }

    fun applyOverrides(config: TopicConfigCreator.PreviewTopicConfigurations): TopicConfigCreator.PreviewTopicConfigurations =
        if (overrideFilePath == null) {
            config
        } else {
            topicConfigCreator.applyOverrides(
                config = config,
                topicPrefix = topic!!.namePrefix,
                overrideFilePath = overrideFilePath
            )
        }

    fun getTopicConfigsForPreview(
        topicConfigurations: List<TopicConfigCreator.TopicConfig>
    ): TopicConfigCreator.PreviewTopicConfigurations {
        return topicConfigCreator.getTopicConfigsForPreview(
            topicConfigurations = topicConfigurations,
            topicPrefix = topic!!.namePrefix,
            partitionOverride = partitionOverride,
            replicaOverride = replicaOverride,
            kafkaUsers = kafkaUsers
        )
    }
}
