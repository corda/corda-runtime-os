package net.corda.applications.workers.workercommon

import net.corda.schema.configuration.BootConfig
import picocli.CommandLine.Option
import java.nio.file.Path

/** The startup parameters handled by all workers. */
class DefaultWorkerParams(healthPortOverride: Int = WORKER_SERVER_PORT) {
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
        names = ["-M", "--max-allowed-message-size"],
        description = ["The maximum message size in bytes allowed to be sent to the message bus."]
    )
    var maxAllowedMessageSize: Int? = null

    @Option(
        names = ["-p", "--worker-server-port"],
        description = ["The port the worker http server should listen on. Defaults to $WORKER_SERVER_PORT."]
    )
    var workerServerPort = healthPortOverride

    @Option(names = ["-m", "--messaging-params"], description = ["Messaging parameters for the worker."])
    var messaging = emptyMap<String, String>()

    @Option(names = ["-s", "--${BootConfig.BOOT_SECRETS}"], description = ["Secrets parameters for the worker."], required = true)
    var secrets = emptyMap<String, String>()

    @Option(names = ["--workspace-dir"], description = ["Corda workspace directory."])
    var workspaceDir: String? = null

    @Option(names = ["--temp-dir"], description = ["Corda temp directory."])
    var tempDir: String? = null

    @Option(names = ["-a", "--addon"], description = ["Add-on configuration"])
    var addon = emptyMap<String, String>()

    @Option(names = ["-f", "--values"], description = ["Load configuration from a file. " +
            "This configuration is merged in with the configuration set in the command line flags. " +
            "Command line flags win. " +
            "When multiple files are specified, values in the right-most file wins."])
    var configFiles = emptyList<Path>()

    @Option(names = ["--send-trace-to"], description = ["URL of server that accepts Zipkin format traces."])
    var zipkinTraceUrl: String? = null

    @Option(names = ["--trace-samples-per-second"], description = ["Number of request traces to sample per second, " +
            "defaults to 1 sample per second. Set to \"unlimited\" to record all samples"])
    var traceSamplesPerSecond: String? = null

    @Option(names = ["--metrics-keep-names"], description = ["A regular expression for the names of metrics that " +
            "Corda should keep; if unspecified, defaults to keeping all metrics"])
    var metricsKeepNames: String? = null

    @Option(names = ["--metrics-drop-labels"], description = ["A regular expression for the names of metric labels " +
            "that Corda should drop; if unspecified, defaults to keeping all labels"])
    var metricsDropLabels: String? = null

    @Option(names = ["--mediator-replicas-flow-session"], description = ["Sets the number of mediators that consume " +
            "flow session messages"])
    var mediatorReplicasFlowSession: Int? = null

    @Option(names = ["--mediator-replicas-flow-session-in"], description = ["Sets the number of mediators that " +
            "consume flow mapper session in messages"])
    var mediatorReplicasFlowMapperSessionIn: Int? = null

    @Option(names = ["--mediator-replicas-flow-session-out"], description = ["Sets the number of mediators that " +
            "consume flow mapper session out messages"])
    var mediatorReplicasFlowMapperSessionOut: Int? = null
}
