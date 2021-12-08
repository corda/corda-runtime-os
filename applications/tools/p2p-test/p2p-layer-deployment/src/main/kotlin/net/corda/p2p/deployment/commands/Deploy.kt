package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.pods.DbDetails
import net.corda.p2p.deployment.pods.InfrastructureDetails
import net.corda.p2p.deployment.pods.Namespace
import net.corda.p2p.deployment.pods.NamespaceIdentifier
import net.corda.p2p.deployment.pods.P2PDeploymentDetails
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "deploy",
    showDefaultValues = true,
    description = ["Deploy P2P layer cluster for K8S"]
)
class Deploy : Runnable {
    @Option(
        names = ["--debug"],
        description = ["Enable Debug"]
    )
    private var debug = false

    @Option(
        names = ["-g", "--gateway-count"],
        description = ["Number of Gateways in the cluster"]
    )
    private var gatewayCount = 2
    @Option(
        names = ["-l", "--link-manager-count"],
        description = ["Number of Link Managers in the cluster"]
    )
    private var linkManagerCount = 2

    @Option(
        names = ["-H", "--host"],
        description = ["The host name"]
    )
    var hostName: String? = null

    @Option(
        names = ["-x", "--x500-name"],
        description = ["The X 500 name"]
    )
    private var x500Name: String? = null

    @Option(
        names = ["--group-id"],
        description = ["The group ID"]
    )
    private var groupId = "group-1"

    @Option(
        names = ["-k", "--kafka-brokers"],
        description = ["Number of kafka brokers in the cluster"]
    )
    private var kafkaBrokerCount = 3

    @Option(
        names = ["--disable-kafka-ui"],
        description = ["Disable Kafka UI"]
    )
    private var disableKafkaUi = false

    @Option(
        names = ["-z", "--zoo-keepers-count"],
        description = ["Number of Zoo Keepers in the cluster"]
    )
    private var zooKeeperCount = 1

    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    lateinit var namespaceName: String

    @Option(
        names = ["--db-init-sql-file"],
        description = ["A file name with the initial SQL to create the databases"]
    )
    private var sqlInitFile: File? = null

    @Option(
        names = ["--db-username"],
        description = ["The database username"]
    )
    private var dbUsername: String = "corda"

    @Option(
        names = ["--db-password"],
        description = ["The database password"]
    )
    private var dbPassword: String = "corda-p2p-masters"

    @Option(
        names = ["-d", "--dry-run"],
        description = ["Just output the yaml the stdout, do not interact with K8s"]
    )
    private var dryRun = false

    @Option(
        names = ["-t", "--tag"],
        description = ["The docker name of the tag to pull"]
    )
    private var tag = "5.0.0.0-beta-1638952927164"

    @Option(
        names = ["--lm-conf", "--link-manager-config"],
        description = ["Link manager extra configuration arguments"]
    )
    var linkManagerExtraArguments = listOf("--sessionTimeoutMilliSecs", "1800000")

    @Option(
        names = ["--gateway-config", "--gateway-conf"],
        description = ["Gateway extra configuration arguments"]
    )
    var gatewayArguments = listOf(
        "--responseTimeoutMilliSecs", "1800000",
        "--connectionIdleTimeoutSec", "1800",
        "--retryDelayMilliSecs", "100000",
    )

    override fun run() {
        val namespace = Namespace(
            NamespaceIdentifier(
                namespaceName,
                x500Name ?: "O=$namespaceName, L=London, C=GB",
                groupId,
                hostName ?: "www.$namespaceName.com"
            ),
            P2PDeploymentDetails(
                linkManagerCount,
                gatewayCount,
                debug,
                tag
            ),
            InfrastructureDetails(
                kafkaBrokerCount,
                zooKeeperCount,
                disableKafkaUi,
                DbDetails(
                    dbUsername,
                    dbPassword,
                    sqlInitFile
                )
            )
        )
        val writer = ObjectMapper(YAMLFactory()).writer()
        if (dryRun) {
            val pods = namespace.infrastructurePods + namespace.p2pPods
            val yamls = namespace.nameSpaceYaml + pods.flatMap { it.yamls(namespaceName) }
            val rawYaml = yamls.joinToString("\n") {
                writer.writeValueAsString(it)
            }
            println(rawYaml)
        } else {
            val delete = Destroy()
            delete.namespaceName = namespaceName
            delete.run()

            println("Creating namespace $namespaceName...")

            DeployYamls(namespace.nameSpaceYaml).run()
            DeployPods(namespaceName, namespace.infrastructurePods).run()

            println("Creating/alerting kafka topics...")
            KafkaSetup(namespaceName, kafkaBrokerCount).run()

            DeployPods(namespaceName, namespace.p2pPods).run()

            configureNamespace()
            println("Cluster $namespaceName is deployed")
        }
    }

    private fun configureNamespace() {
        UpdateIps().run()
        val config = ConfigureAll()
        config.namespaceName = namespaceName
        config.linkManagerExtraArguments = linkManagerExtraArguments
        config.gatewayArguments = gatewayArguments
        config.run()
    }
}
