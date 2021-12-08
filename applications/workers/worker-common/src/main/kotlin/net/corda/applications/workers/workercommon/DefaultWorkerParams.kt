package net.corda.applications.workers.workercommon

import picocli.CommandLine.Option
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The startup parameters handled by all workers. */
class DefaultWorkerParams {
    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit."])
    var helpRequested = false

    @Option(names = ["-v", "--version"], description = ["Display version and exit."])
    var versionRequested = false

    @Suppress("Unused")
    @Option(
        names = ["-i", "--instanceId"],
        description = ["The Kafka instance ID for this worker. Defaults to a random value."]
    )
    var instanceId = Random.nextInt().absoluteValue

    @Suppress("Unused")
    @Option(
        names = ["-t", "--topicPrefix"],
        description = ["The prefix to use for Kafka topics. Defaults to the empty string."]
    )
    var topicPrefix = ""

    @Option(names = ["-d", "--disableHealthMonitor"], description = ["Disables the health monitor."])
    var disableHealthMonitor = false

    @Option(
        names = ["-p", "--healthMonitorPort"],
        description = ["The port the health monitor should listen on. Defaults to $HEALTH_MONITOR_PORT."]
    )
    var healthMonitorPort = HEALTH_MONITOR_PORT

    @Option(names = ["-c", "--additionalParams"], description = ["Additional parameters for the worker."])
    var additionalParams = emptyMap<String, String>()
}