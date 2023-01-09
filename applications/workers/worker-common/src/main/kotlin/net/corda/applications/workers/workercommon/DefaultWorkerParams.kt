package net.corda.applications.workers.workercommon

import kotlin.math.absoluteValue
import kotlin.random.Random
import net.corda.applications.workers.workercommon.internal.WORKER_MONITOR_PORT
import net.corda.schema.configuration.ConfigDefaults
import picocli.CommandLine.Option

/** The startup parameters handled by all workers. */
class DefaultWorkerParams {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit."])
    var helpRequested = false

    @Option(names = ["-v", "--version"], description = ["Display version and exit."])
    var versionRequested = false

    @Option(
        names = ["-i", "--instance-id"],
        description = ["The Kafka instance ID for this worker. Defaults to a random value."]
    )
    var instanceId = Random.nextInt().absoluteValue

    @Option(
        names = ["-t", "--topic-prefix"],
        description = ["The prefix to use for Kafka topics. Defaults to the empty string."]
    )
    // This needs revision as arguably it belongs to the `messagingParams`
    var topicPrefix = ""

    @Option(names = ["-n", "--no-worker-monitor"], description = ["Disables the worker monitor."])
    var disableWorkerMonitor = false

    @Option(
        names = ["-p", "--worker-monitor-port"],
        description = ["The port the worker monitor should listen on. Defaults to $WORKER_MONITOR_PORT."]
    )
    var workerMonitorPort = WORKER_MONITOR_PORT

    @Option(names = ["-m", "--messaging-params"], description = ["Messaging parameters for the worker."])
    var messagingParams = emptyMap<String, String>()

    @Option(names = ["-s", "--secrets-params"], description = ["Secrets parameters for the worker."])
    var secretsParams = emptyMap<String, String>()

    @Option(names = ["--workspace-dir"], description = ["Corda workspace directory."])
    var workspaceDir = ConfigDefaults.WORKSPACE_DIR

    @Option(names = ["--temp-dir"], description = ["Corda temp directory."])
    var tempDir = ConfigDefaults.TEMP_DIR

    @Option(names = ["-a", "--addon"], description = ["Add-on configuration"])
    var addonParams = emptyMap<String, String>()
}