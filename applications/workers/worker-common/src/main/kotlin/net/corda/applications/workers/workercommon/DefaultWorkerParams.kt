package net.corda.applications.workers.workercommon

import net.corda.applications.workers.workercommon.internal.WORKER_MONITOR_PORT
import picocli.CommandLine.Option
import java.nio.file.Path

/** The startup parameters handled by all workers. */
class DefaultWorkerParams(healthPortOverride: Int = WORKER_MONITOR_PORT) {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit."])
    var helpRequested = false

    @Option(names = ["-v", "--version"], description = ["Display version and exit."])
    var versionRequested = false

    @Option(
        names = ["-i", "--instance-id"],
        description = ["The Kafka instance ID for this worker. Defaults to a random value."]
    )
    var instanceId: Int? = null

    @Option(
        names = ["-t", "--topic-prefix"],
        description = ["The prefix to use for Kafka topics. Defaults to the empty string."]
    )
    // This needs revision as arguably it belongs to the `messagingParams`
    var topicPrefix : String? = null

    // This needs revision as arguably it belongs to the `messagingParams`. Defaulting to 1MB to match kafkas default and our config
    // schema default
    @Option(
        names = ["-M", "--max-message-size"],
        description = ["The maximum message size in bytes allowed to be sent to the message bus."]
    )
    var maxAllowedMessageSize: Int? = null

    @Option(names = ["-n", "--no-worker-monitor"], description = ["Disables the worker monitor."])
    var disableWorkerMonitor = false

    @Option(
        names = ["-p", "--worker-monitor-port"],
        description = ["The port the worker monitor should listen on. Defaults to $WORKER_MONITOR_PORT."]
    )
    var workerMonitorPort = healthPortOverride

    @Option(names = ["-m", "--messaging-params"], description = ["Messaging parameters for the worker."])
    var messagingParams = emptyMap<String, String>()

    @Option(names = ["-s", "secrets"], description = ["Secrets parameters for the worker."], required = true)
    var secretsParams = emptyMap<String, String>()

    @Option(names = ["--workspace-dir"], description = ["Corda workspace directory."])
    var workspaceDir: String? = null

    @Option(names = ["--temp-dir"], description = ["Corda temp directory."])
    var tempDir: String? = null

    @Option(names = ["-a", "--addon"], description = ["Add-on configuration"])
    var addonParams = emptyMap<String, String>()

    @Option(names = ["-f", "--values"], description = ["Load configuration from a file. " +
            "This configuration is merged in with the configuration set in the command line flags. " +
            "Command line flags win. " +
            "When multiple files are specified, values in the right-most file wins."])
    var configFiles = emptyList<Path>()
}