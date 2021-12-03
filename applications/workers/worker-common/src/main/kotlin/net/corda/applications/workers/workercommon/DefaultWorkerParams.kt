package net.corda.applications.workers.workercommon

import picocli.CommandLine.Option
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The startup parameters handled by all workers. */
class DefaultWorkerParams {
    @Suppress("Unused")
    @Option(names = ["--instanceId"], description = ["The Kafka instance ID for this worker."])
    var instanceId = Random.nextInt().absoluteValue

    @Option(names = ["--disableHealthMonitor"], description = ["Disables the health monitor."])
    var disableHealthMonitor = false

    @Option(names = ["--healthMonitorPort"], description = ["The port for the health monitor."])
    var healthMonitorPort = HEALTH_MONITOR_PORT

    @Option(names = ["-c"], description = ["Additional parameters for the processor."])
    var additionalParams = emptyMap<String, String>()
}