package net.corda.applications.workers.workercommon.internal

import picocli.CommandLine
import kotlin.math.absoluteValue
import kotlin.random.Random

/** The expected parameters in the arguments to the worker. */
internal class WorkerParams {
    // TODO - Joel - Allow individual workers to add additional commands.
    @CommandLine.Option(names = [PARAM_INSTANCE_ID], description = ["The Kafka instance ID for this worker."])
    var instanceId = Random.nextInt().absoluteValue

    @CommandLine.Option(names = [PARAM_EXTRA], description = ["Additional parameters for the processor."])
    var additionalParams = emptyMap<String, String>()
}