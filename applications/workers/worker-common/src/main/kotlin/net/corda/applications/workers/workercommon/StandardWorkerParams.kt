package net.corda.applications.workers.workercommon

import picocli.CommandLine.Option
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The startup parameters handled by all workers. */
class StandardWorkerParams {
    @Suppress("Unused")
    @Option(names = [PARAM_INSTANCE_ID], description = ["The Kafka instance ID for this worker."])
    var instanceId = Random.nextInt().absoluteValue

    @Option(names = [PARAM_DISABLE_HEALTH_MONITOR], description = ["Disables the health monitor."])
    var disableHealthMonitor = false

    @Option(names = [PARAM_HEALTH_MONITOR_PORT], description = ["The port for the health monitor."])
    var healthMonitorPort = HEALTH_MONITOR_PORT

    @Option(names = [PARAM_EXTRA], description = ["Additional parameters for the processor."])
    var additionalParams = emptyMap<String, String>()
}