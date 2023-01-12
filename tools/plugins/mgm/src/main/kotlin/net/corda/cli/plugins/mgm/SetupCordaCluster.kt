package net.corda.cli.plugins.mgm

import kong.unirest.Unirest
import kong.unirest.json.JSONArray
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.min

@Command(
    name = "setupCluster",
    aliases = ["cluster"],
    description = [
        "Setup a test Corda cluster on Kubernetes",
        "This sub command should only be used in for internal development.",
    ]
)
class SetupCordaCluster : Runnable {
    private companion object {
        const val PREFIX = "5.0.0.0-beta-"
    }

    @Parameters(
        description = ["The name of the clusters (the Kubernetes namespaces to create)"],
        paramLabel = "NAME",
        arity = "1..*"
    )
    lateinit var cordaClusterNames: List<String>

    @Option(
        names = ["--awk-name", "-a"],
        description = ["The name of the AWS context to use. Leave empty to use the current one."]
    )
    var awsName: String? = null

    @Option(
        names = ["--minikube", "-m"],
        description = ["Use minikube."]
    )
    var minikube: Boolean = false

    @Option(
        names = ["--corda-runtime-os-dir", "-r"],
        description = ["Runtime OS directory (default to ~/corda-runtime-os)."]
    )
    var cordaOsRuntimeDir: File = File(File(System.getProperty("user.home")), "corda-runtime-os")

    @Option(
        names = ["--base-image", "-b"],
        description = ["The base image to use. Leave empty for the latest release"]
    )
    var baseImage: String? = null

    @Option(
        names = ["--kafka-replicas", "--kafka", "-k"],
        description = ["The number of Kafka replicas to setup. Default to 3"]
    )
    var kafkaReplicas: Int = 3

    @Option(
        names = ["--zoo-keeper-replicas", "--zk", "-z"],
        description = ["The number of Zoo Keeper replicas to setup. Default to 3"]
    )
    var zooKeeperReplicas: Int = 3

    @Option(
        names = ["--corda-e2e-type", "--corda-e2e", "--e2e"],
        description = ["Mark the namespace as corda-e2e."]
    )
    var cordaE2eType: Boolean = false

    private fun execute(command: String, vararg arguments: String) {
        val process = ProcessBuilder()
            .command(listOf(command) + arguments)
            .inheritIO()
            .start()
        if (process.waitFor() != 0) {
            throw SetupException("Could not run $command")
        }
    }

    private fun kubectl(vararg arguments: String) {
        execute("kubectl", *arguments)
    }
    private fun helm(vararg arguments: String) {
        execute("helm", *arguments)
    }

    private fun deploy(clusterName: String) {
        kubectl("delete", "ns", clusterName, "--ignore-not-found=true")
        kubectl("create", "ns", clusterName)
        if (cordaE2eType) {
            kubectl("label", "ns", clusterName, "namespace-type=corda-e2e", "--overwrite=true")
        }


        try {
            kubectl(
                "create", "secret", "docker-registry", "docker-registry-cred",
                "--docker-server=corda-os-docker.software.r3.com",
                "--docker-username=${System.getenv("CORDA_ARTIFACTORY_USERNAME")}",
                "--docker-password=${System.getenv("CORDA_ARTIFACTORY_PASSWORD")}",
                "-n", clusterName
            )
        } catch (e: SetupException) {
            // Do nothing, the secret might already be there
        }

        val prereqsYaml = File(File(File(cordaOsRuntimeDir, ".ci"), "e2eTests"), "prereqs.yaml")
        val prereqsEksYaml = File(File(File(cordaOsRuntimeDir, ".ci"), "e2eTests"), "prereqs-eks.yaml")
        val replicationFactor = min(kafkaReplicas, 3)
        helm(
            "install", "prereqs", "oci://corda-os-docker.software.r3.com/helm-charts/corda-dev",
            "-f", prereqsYaml.absolutePath,
            "-f", prereqsEksYaml.absolutePath,
            "--set", "kafka.replicaCount=$kafkaReplicas," +
                "kafka.zookeeper.replicaCount=$zooKeeperReplicas," +
                "kafka.auth.clientProtocol=tls," +
                "kafka.offsetsTopicReplicationFactor=$replicationFactor," +
                "kafka.transactionStateLogReplicationFactor=$replicationFactor",
            "-n", clusterName, "--wait", "--timeout", "600s"
        )

        val chart = File(cordaOsRuntimeDir, "charts")
        val cordaChart = File(chart, "corda")
        val cordaYaml = File(File(File(cordaOsRuntimeDir, ".ci"), "e2eTests"), "corda.yaml")
        val cordaEksYaml = File(File(File(cordaOsRuntimeDir, ".ci"), "e2eTests"), "corda-eks.yaml")
        helm(
            "install",
            "corda", cordaChart.absolutePath,
            "-f", cordaYaml.absolutePath,
            "-f", cordaEksYaml.absolutePath,
            "--set",
            "image.tag=$actualBaseImage," +
                "bootstrap.kafka.replicas=$kafkaReplicas," +
                "kafka.sasl.enabled=false," +
                "db.cluster.host=prereqs-postgresql," +
                "db.cluster.password.valueFrom.secretKeyRef.name=prereqs-postgresql",
            "-n", clusterName, "--wait", "--timeout", "600s"
        )
        if (!cordaE2eType) {
            println("Don't forget to delete the namespace $clusterName by running:")
            println("kubectl delete ns $clusterName")
        }
    }

    private val actualBaseImage by lazy {
        if (baseImage == null) {
            val tagsReply = Unirest.get(
                "https://corda-os-docker-unstable.software.r3.com:443/v2/corda-os-p2p-link-manager-worker/tags/list"
            )
                .basicAuth(
                    System.getenv("CORDA_ARTIFACTORY_USERNAME"),
                    System.getenv("CORDA_ARTIFACTORY_PASSWORD"),
                ).asJson()
            if (!tagsReply.isSuccess) {
                throw SetupException("Could not get list of tags. ${tagsReply.statusText}")
            }
            val tagsList = tagsReply.body.`object`?.get("tags")
                as? JSONArray
                ?: throw SetupException("Tags has the wrong format - ${tagsReply.body}")
            tagsList.filterNotNull()
                .map {
                    it.toString()
                }.filter { it.startsWith(PREFIX) }.maxByOrNull {
                    it.removePrefix(PREFIX).toLongOrNull() ?: 0
                }?.also {
                    println("Using base image: $it")
                } ?: throw SetupException("Could not find any tag")
        } else {
            baseImage
        }
    }

    override fun run() {
        println("This sub command should only be used in for internal development")
        if (minikube) {
            kubectl("config", "use-context", "minikube")
        }
        if (awsName != null) {
            execute(
                "aws",
                "eks",
                "update-kubeconfig",
                "--name",
                awsName!!
            )
        }

        cordaClusterNames.map { networkName ->
            thread {
                deploy(networkName)
            }
        }.forEach {
            it.join()
        }
    }

    private class SetupException(message: String) : Exception(message)
}
