package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.commands.ConfigureAll
import net.corda.p2p.deployment.commands.Destroy
import net.corda.p2p.deployment.commands.UpdateIps
import net.corda.p2p.deployment.pods.Gateway
import net.corda.p2p.deployment.pods.KafkaBroker
import net.corda.p2p.deployment.pods.LinkManager
import net.corda.p2p.deployment.pods.PostGreSql
import net.corda.p2p.deployment.pods.Simulator
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import kotlin.concurrent.thread

typealias Yaml = Map<String, Any?>

@Command(
    name = "deploy",
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

    val actualHostName : String
        get() =
            hostName ?: "www.$namespaceName.com"

    @Option(
        names = ["-x", "--x500-name"],
        description = ["The X 500 name"]
    )
    private var x500Name : String? = null

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
        description = ["The name of the namespace"]
    )
    var namespaceName = "p2p-layer"

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
        names = ["--storage-class"],
        description = ["The storage class name"]
    )
    var storageClassName: String = "standard"

    @Option(
        names = ["--no-volume-creation"],
        description = ["Avoid creating any volumes"]
    )
    var noVolumes = false

    @Option(
        names = ["-d", "--dry-run"],
        description = ["Just output the yaml the stdout, do not interact with K8s"]
    )
    private var dryRun = false

    @Option(
        names = ["-t", "--tag"],
        description = ["The docker name of the tag to pull"]
    )
    private var tag = "5.0.0.0-alpha-1637835488501"

    @Option(
        names = ["--lm-conf", "--link-manager-config"],
        description = ["Link manager extra configuration arguments"]
    )
    var linkManagerExtraArguments = emptyList<String>()

    @Option(
        names = ["--gateway-config", "--gateway-conf"],
        description = ["Gateway extra configuration arguments"]
    )
    var gatewayArguments = emptyList<String>()

    private val nameSpaceYaml by lazy {
        listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Namespace",
                "metadata" to mapOf(
                    "name" to namespaceName,
                    "labels" to mapOf(
                        "namespace-type" to "p2p-deployment",
                    ),
                    "annotations" to mapOf(
                        "type" to "p2p",
                        "x500-name" to (x500Name?:"O=$namespaceName, L=London, C=GB"),
                        "group-id" to groupId,
                        "host" to actualHostName,
                    )
                )
            ),
            CordaOsDockerDevSecret.secret(namespaceName),
        )
    }

    private val kafkaServers by lazy {
        KafkaBroker.kafkaServers(namespaceName, kafkaBrokerCount)
    }

    private val pods by lazy {
        KafkaBroker.kafka(namespaceName, zooKeeperCount, kafkaBrokerCount, kafkaUi) +
            PostGreSql(dbUsername, dbPassword, sqlInitFile) +
            Gateway.gateways(gatewayCount, listOf(actualHostName), kafkaServers, tag, debug) +
            LinkManager.linkManagers(linkManagerCount, kafkaServers, tag, debug) +
            Simulator(kafkaServers, tag, 1024, debug)
    }

    private val yamls by lazy {
        nameSpaceYaml + pods.flatMap { it.yamls(this) }
    }

    override fun run() {
        val writer = ObjectMapper(YAMLFactory()).writer()
        val rawYaml = yamls.joinToString("\n") {
            writer.writeValueAsString(it)
        }
        if (dryRun) {
            println(rawYaml)
        } else {
            val delete = Destroy()
            delete.namespaceName = namespaceName
            delete.run()

            println("Creating namespace $namespaceName...")
            val create = ProcessBuilder().command(
                "kubectl",
                "apply",
                "-f",
                "-"
            ).start()
            thread(isDaemon = true) {
                create.inputStream.reader().useLines {
                    it.forEach { line ->
                        println(line)
                    }
                }
            }
            thread(isDaemon = true) {
                create.errorStream.reader().useLines {
                    it.forEach { line ->
                        System.err.println(line)
                    }
                }
            }
            create.outputStream.write(rawYaml.toByteArray())
            create.outputStream.close()
            if (create.waitFor() != 0) {
                throw DeploymentException("Could not create $namespaceName")
            }
            waitForCluster()
            println("Cluster $namespaceName is deployed")
        }
    }

    private fun waitForCluster() {
        repeat(300) {
            Thread.sleep(1000)
            val listPods = ProcessBuilder().command(
                "kubectl",
                "get",
                "pod",
                "-n",
                namespaceName
            ).start()
            if (listPods.waitFor() != 0) {
                throw DeploymentException("Could not get the pods in $namespaceName")
            }
            val waitingFor = listPods.inputStream
                .reader()
                .readLines()
                .drop(1)
                .map {
                    it.split("\\s+".toRegex())
                }.map {
                    it[0] to it[2]
                }.filter {
                    it.second != "Running"
                }.toMap()
            val badContainers = waitingFor.filterValues {
                it == "Error" || it == "CrashLoopBackOff"
            }
            if (badContainers.isNotEmpty()) {
               println("Error in ${badContainers.keys}")
               badContainers.keys.forEach {
                   ProcessBuilder().command(
                       "kubectl",
                       "describe",
                       "pod",
                       "-n",
                       namespaceName,
                       it
                   ).inheritIO()
                       .start()
                       .waitFor()
                   throw DeploymentException("Error in pods")
               }
            }
            if (waitingFor.isEmpty()) {
                configureNamespace()
                return
            } else {
                println("Waiting for:")
                waitingFor.forEach { (name, status) ->
                    println("\t $name ($status)")
                }
            }
        }
        throw DeploymentException("Waiting too long for $namespaceName")
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
