package net.corda.applications.workers.workercommon

import net.corda.applications.workers.workercommon.internal.HEALTH_MONITOR_PORT
import net.corda.schema.configuration.ConfigDefaults
import picocli.CommandLine.Option
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The startup parameters handled by all workers. */
class DefaultWorkerParams {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit."])
    var helpRequested = false

    @Option(names = ["-v", "--version"], description = ["Display version and exit."])
    var versionRequested = false

    @Option(
        names = ["-i", "--instanceId"],
        description = ["The Kafka instance ID for this worker. Defaults to a random value."]
    )
    var instanceId = Random.nextInt().absoluteValue

    @Option(
        names = ["-t", "--topicPrefix"],
        description = ["The prefix to use for Kafka topics. Defaults to the empty string."]
    )
    // This needs revision as arguably it belongs to the `messagingParams`
    var topicPrefix = ""

    @Option(names = ["-n", "--noHealthMonitor"], description = ["Disables the health monitor."])
    var disableHealthMonitor = false

    @Option(
        names = ["-p", "--healthMonitorPort"],
        description = ["The port the health monitor should listen on. Defaults to $HEALTH_MONITOR_PORT."]
    )
    var healthMonitorPort = HEALTH_MONITOR_PORT

    @Option(names = ["-m", "--messagingParams"], description = ["Messaging parameters for the worker."])
    var messagingParams = emptyMap<String, String>()

    @Option(names = ["-s", "--secretsParams"], description = ["Secrets parameters for the worker."])
    var secretsParams = emptyMap<String, String>()

    @Option(names = ["--workspace-dir"], description = ["Corda worspace directory."])
    var workspaceDir = ConfigDefaults.WORKSPACE_DIR

    @Option(names = ["--temp-dir"], description = ["Corda temp directory."])
    var tempDir = ConfigDefaults.TEMP_DIR

    @Option(names = ["-c", "--additionalParams"], description = ["Additional parameters for the worker."])
    var additionalParams = emptyMap<String, String>()
}