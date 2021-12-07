package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.commands.ConfigureAll
import net.corda.p2p.deployment.commands.DeployPods
import net.corda.p2p.deployment.commands.DeployYamls
import net.corda.p2p.deployment.commands.Destroy
import net.corda.p2p.deployment.commands.KafkaSetup
import net.corda.p2p.deployment.commands.MyUserName
import net.corda.p2p.deployment.commands.UpdateIps
import net.corda.p2p.deployment.pods.Gateway
import net.corda.p2p.deployment.pods.KafkaBroker
import net.corda.p2p.deployment.pods.LinkManager
import net.corda.p2p.deployment.pods.PostGreSql
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

typealias Yaml = Map<String, Any?>

@Command(
    name = "deploy",
    showDefaultValues = true,
    description = ["Deploy P2P layer cluster for K8S"]
)
class Namespace : Runnable {
    @Option(
        names = ["--debug"],
        description = ["Enable Debug"]
    )
    private var debug = false

    @Option(
        names = ["-g", "--gateway-count"],
        description = ["Number of Gateways in the cluster"]
    )
    private var gatewayCount = 3
    @Option(
        names = ["-l", "--link-manager-count"],
        description = ["Number of Link Managers in the cluster"]
    )
    private var linkManagerCount = 3

    @Option(
        names = ["-H", "--host"],
        description = ["The host name"]
    )
    var hostName: String? = null

    private val actualHostName: String
        get() =
            hostName ?: "www.$namespaceName.com"

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
        names = ["--kafka-ui"],
        description = ["Enable Kafka UI"]
    )
    private var kafkaUi = false

    @Option(
        names = ["-z", "--zoo-keepers-count"],
        description = ["Number of Zoo Keepers in the cluster"]
    )
    private var zooKeeperCount = 3

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
    private var tag = "5.0.0.0-beta-1638524084494"

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

    private val nameSpaceYaml by lazy {
        listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Namespace",
                "metadata" to mapOf(
                    "name" to namespaceName,
                    "labels" to mapOf(
                        "namespace-type" to "p2p-deployment",
                        "creator" to MyUserName.userName,
                    ),
                    "annotations" to mapOf(
                        "type" to "p2p",
                        "x500-name" to (x500Name ?: "O=$namespaceName, L=London, C=GB"),
                        "group-id" to groupId,
                        "host" to actualHostName,
                        "debug" to debug.toString(),
                        "tag" to tag,
                        "kafkaServers" to kafkaServers
                    )
                )
            ),
            CordaOsDockerDevSecret.secret(namespaceName),
        )
    }

    private val kafkaServers by lazy {
        KafkaBroker.kafkaServers(namespaceName, kafkaBrokerCount)
    }

    private val infrastructurePods by lazy {
        KafkaBroker.kafka(namespaceName, zooKeeperCount, kafkaBrokerCount, kafkaUi) +
            PostGreSql(dbUsername, dbPassword, sqlInitFile)
    }

    private val p2pPodsPods by lazy {
        Gateway.gateways(gatewayCount, listOf(actualHostName), kafkaServers, tag, debug) +
            LinkManager.linkManagers(linkManagerCount, kafkaServers, tag, debug)
    }

    override fun run() {
        val writer = ObjectMapper(YAMLFactory()).writer()
        if (dryRun) {
            val pods = infrastructurePods + p2pPodsPods
            val yamls = nameSpaceYaml + pods.flatMap { it.yamls(namespaceName) }
            val rawYaml = yamls.joinToString("\n") {
                writer.writeValueAsString(it)
            }
            println(rawYaml)
        } else {
            val delete = Destroy()
            delete.namespaceName = namespaceName
            delete.run()

            println("Creating namespace $namespaceName...")

            DeployYamls(nameSpaceYaml).run()
            DeployPods(this, infrastructurePods).run()

            println("Creating/alerting kafka topics...")
            KafkaSetup(namespaceName).run()

            DeployPods(this, p2pPodsPods).run()

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
