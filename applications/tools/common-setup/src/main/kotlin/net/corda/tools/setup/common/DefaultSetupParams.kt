package net.corda.tools.setup.common

import picocli.CommandLine
import java.io.File

class DefaultSetupParams {
    @CommandLine.Option(
        names = ["--instanceId"],
        description = ["InstanceId for a transactional publisher, leave blank to use async publisher"]
    )
    var instanceId: String? = null

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["--topic"], description = ["File containing the topic template"])
    var topicTemplate: File? = null
}
