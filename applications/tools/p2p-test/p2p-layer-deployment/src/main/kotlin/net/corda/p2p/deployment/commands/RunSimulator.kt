package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import net.corda.p2p.deployment.pods.Simulator
import picocli.CommandLine.Option

abstract class RunSimulator : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true
    )
    lateinit var namespaceName: String

    @Option(
        names = ["-l", "--follow"],
        description = ["Follow the simulator output"],
    )
    private var follow = false

    abstract val parameters: Yaml

    private val namespaceAnnotation by lazy {
        val getNamespace = ProcessBuilder().command(
            "kubectl",
            "get", "ns",
            "--field-selector", "metadata.name=$namespaceName",
            "-o",
            "jsonpath={.items[*].metadata.annotations}"
        ).start()
        if (getNamespace.waitFor() != 0) {
            System.err.println(getNamespace.errorStream.reader().readText())
            throw DeploymentException("Could not get namespace")
        }
        val json = getNamespace.inputStream.reader().readText()
        if (json.isBlank()) {
            throw DeploymentException("Could not find namespace $namespaceName")
        }
        val reader = ObjectMapper().reader()
        reader.readValue(json, Map::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    val dbParams by lazy {
        val getDb = ProcessBuilder().command(
            "kubectl",
            "get", "pods",
            "-n", namespaceName,
            "-l", "app=db",
            "-o", "jsonpath={.items[*].spec.containers[].env}"
        ).start()
        if (getDb.waitFor() != 0) {
            System.err.println(getDb.errorStream.reader().readText())
            throw DeploymentException("Could not get DB pod")
        }

        val json = getDb.inputStream.reader().readText()
        if (json.isBlank()) {
            throw DeploymentException("Could not DB pod in namespace $namespaceName")
        }
        val reader = ObjectMapper().reader()
        val env = reader.readValue(json, List::class.java) as Collection<Yaml>
        val password = env.firstOrNull {
            it["name"] == "POSTGRES_PASSWORD"
        }?.let {
            it["value"] as? String
        }
        val username = env.firstOrNull {
            it["name"] == "POSTGRES_USER"
        }?.let {
            it["value"] as? String
        }
        mapOf(
            "username" to username,
            "password" to password,
            "host" to "db.$namespaceName",
            "db" to username
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val configFile = ObjectMapper().writeValueAsString(parameters)
        val job = Simulator(
            namespaceAnnotation["kafkaServers"] as String,
            namespaceAnnotation["tag"] as String,
            configFile
        )
        DeployYamls(job.yamls(namespaceName)).run()
        if (follow) {
            followPod(job.app)
        }
    }

    private fun followPod(app: String) {
        val getPodName = ProcessBuilder().command(
            "kubectl", "get", "pods",
            "-n", namespaceName,
            "--selector=job-name=$app",
            "--output=jsonpath={.items[*].metadata.name}"
        ).start()
        if (getPodName.waitFor() != 0) {
            System.err.println(getPodName.errorStream.reader().readText())
            throw DeploymentException("Could not get pod")
        }
        val podName = getPodName.inputStream.reader().readText()

        waitForPod(podName)

        val log = ProcessBuilder().command(
            "kubectl",
            "logs",
            "-n", namespaceName,
            podName,
            "-f"
        ).inheritIO().start()
        log.waitFor()
    }

    private fun isRunning(podName: String): Boolean {
        val getStatus = ProcessBuilder().command(
            "kubectl",
            "get", "pods",
            "-n", namespaceName,
            "--field-selector", "metadata.name=$podName",
            "--output=jsonpath={.items[*].status.phase}",
        ).start()
        if (getStatus.waitFor() != 0) {
            System.err.println(getStatus.errorStream.reader().readText())
            throw DeploymentException("Could not get job status")
        }

        return when (getStatus.inputStream.reader().readText()) {
            "Running", "Succeeded" -> true
            "Pending" -> false
            else -> throw DeploymentException("Simulator job had error")
        }
    }
    private fun waitForPod(podName: String) {
        while (!isRunning(podName)) {
            println("Waiting for $podName")
            Thread.sleep(1000)
        }
    }
}
