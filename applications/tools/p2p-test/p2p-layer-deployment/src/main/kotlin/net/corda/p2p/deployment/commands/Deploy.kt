package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.deployment.pods.InfrastructureDetails
import net.corda.p2p.deployment.pods.LbType
import net.corda.p2p.deployment.pods.Namespace
import net.corda.p2p.deployment.pods.NamespaceIdentifier
import net.corda.p2p.deployment.pods.P2PDeploymentDetails
import net.corda.p2p.deployment.pods.ResourceRequest
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "deploy",
    showDefaultValues = true,
    description = ["Deploy P2P layer cluster for K8S"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
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
        description = ["The host name. This will be ignored for headless load balancers"]
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
        names = ["-p", "--partitions", "--kafka-partition-count"],
        description = ["Number of Kafka partition in each topic"]
    )
    private var partitionsCount = 10

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
        names = ["-d", "--dry-run"],
        description = ["Just output the yaml the stdout, do not interact with K8s"]
    )
    private var dryRun = false

    @Option(
        names = ["-t", "--tag"],
        description = ["The docker name of the tag to pull"]
    )
    private var tag = "5.0.0.0-alpha-1649953915034"

    @Option(
        names = ["-a", "--key-algorithm"],
        description = ["The keys algorithm"]
    )
    private var algo = KeyAlgorithm.RSA

    @Option(
        names = ["--trust-store"],
        description = ["The trust store type (\${COMPLETION-CANDIDATES})"]
    )
    private var trustStoreType: TrustStoreType = TrustStoreType.TINY_CERT

    @Option(
        names = ["--lm-conf", "--link-manager-config"],
        description = ["Link manager extra configuration arguments (fop example: --sessionTimeoutMilliSecs=1800000)"]
    )
    var linkManagerExtraArguments = emptyList<String>()

    @Option(
        names = ["--gateway-config", "--gateway-conf"],
        description = ["Gateway extra configuration arguments (for example: --retryDelayMilliSecs=100000)"]
    )
    var gatewayArguments = emptyList<String>()

    private val isMiniKube = let {
        ProcessRunner.execute(
            "kubectl",
            "config",
            "current-context"
        ).contains("minikube")
    }

    @Option(
        names = ["--p2p-memory"],
        description = ["The memory each P2P component will need (for example: 2Gi, 750Mi...)"]
    )
    var p2pMemory = if (isMiniKube) {
        null
    } else {
        "2Gi"
    }

    @Option(
        names = ["--p2p-cpu"],
        description = ["The number of CPUs each P2P component will need (for example: 0.5, 1, 3...)"]
    )
    var p2pCpu = if (isMiniKube) {
        null
    } else {
        1.0
    }

    @Option(
        names = ["--kafka-broker-memory"],
        description = ["The memory each Kafka broker will need (for example: 2Gi, 750Mi...)"]
    )
    var kafkaBrokerMemory = if (isMiniKube) {
        null
    } else {
        "4Gi"
    }

    @Option(
        names = ["--kakfa-broker-cpu"],
        description = ["The number of CPUs each Kafka broker will need (for example: 0.5, 1, 3...)"]
    )
    var kafkaBrokerCpu = if (isMiniKube) {
        null
    } else {
        1.0
    }

    @Option(
        names = ["--load-balancer-type"],
        description = ["The load balancer type (\${COMPLETION-CANDIDATES})"]
    )
    private var lbType: LbType = LbType.HEADLESS

    @Option(
        names = ["--nginx-pods-count"],
        description = ["Number of Nginx load balancer pods in the deployment (If more than one is deployed, the service will be headless)"]
    )
    private var nginxCount: Int = 1

    override fun run() {
        if (hostName != "load-balancer.$namespaceName") {
            if ((lbType == LbType.HEADLESS) || (lbType == LbType.NGINX && nginxCount > 1)) {

                println("For headless LB we will use the host name: load-balancer.$namespaceName")
                hostName = "load-balancer.$namespaceName"
            }
        }

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
                tag,
                ResourceRequest(
                    p2pMemory,
                    p2pCpu,
                ),
                lbType,
                nginxCount,
            ),
            InfrastructureDetails(
                kafkaBrokerCount,
                zooKeeperCount,
                disableKafkaUi,
                ResourceRequest(
                    kafkaBrokerMemory,
                    kafkaBrokerCpu,
                ),
                partitionsCount,
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
            KafkaSetup(namespaceName, kafkaBrokerCount, partitionsCount).run()

            DeployPods(namespaceName, namespace.p2pPods).run()

            configureNamespace()
            println("Cluster $namespaceName is deployed")
        }
    }

    enum class TrustStoreType {
        TINY_CERT, LOCAL
    }

    private fun configureNamespace() {
        UpdateIps().run()
        val config = ConfigureAll()
        config.namespaceName = namespaceName
        config.linkManagerExtraArguments = linkManagerExtraArguments
        config.gatewayArguments = gatewayArguments
        config.algo = algo
        config.trustStoreType = trustStoreType
        config.run()
    }
}
