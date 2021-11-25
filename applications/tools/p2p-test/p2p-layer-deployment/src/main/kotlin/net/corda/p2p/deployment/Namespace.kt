package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.commands.Destroy
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
        names = ["-H", "--hosts"],
        description = ["The hosts names"]
    )
    var hostsNames: List<String> = listOf("www.alice.net")

    @Option(
        names = ["-k", "--kafka-brokers"],
        description = ["Number of kafka brokers in the cluster"]
    )
    private var kafkaBrokerCount = 3

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
    private var tag = "5.0.0.0-alpha-1637757042293"

    private val nameSpaceYaml = listOf(
        mapOf(
            "apiVersion" to "v1",
            "kind" to "Namespace",
            "metadata" to mapOf(
                "name" to namespaceName,
                "labels" to mapOf(
                    "namespace-type" to "p2p-layer"
                )
            )
        ),
        CordaOsDockerDevSecret.secret(namespaceName),
    )

    private val kafkaServers by lazy {
        KafkaBroker.kafkaServers(namespaceName, kafkaBrokerCount)
    }

    private val pods by lazy {
        KafkaBroker.kafka(namespaceName, zooKeeperCount, kafkaBrokerCount) +
            PostGreSql(dbUsername, dbPassword, sqlInitFile) +
            Gateway.gateways(gatewayCount, hostsNames, kafkaServers, tag) +
            LinkManager.linkManagers(linkManagerCount, kafkaServers, tag) +
            Simulator(kafkaServers, tag, 1024)
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
                        System.err.println("err: $line")
                    }
                }
            }
            create.outputStream.write(rawYaml.toByteArray())
            create.outputStream.close()
            if (create.waitFor() != 0) {
                throw Exception("Could not create $namespaceName")
            }
        }
    }
}
