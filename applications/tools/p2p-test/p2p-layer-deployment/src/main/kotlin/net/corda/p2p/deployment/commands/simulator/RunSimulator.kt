package net.corda.p2p.deployment.commands.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import net.corda.p2p.deployment.commands.DeployYamls
import net.corda.p2p.deployment.commands.ProcessRunner
import net.corda.p2p.deployment.commands.simulator.db.Db
import net.corda.p2p.deployment.commands.simulator.db.StartDb
import net.corda.p2p.deployment.pods.Simulator
import java.util.concurrent.ConcurrentHashMap

class RunSimulator(
    private val namespaceName: String,
    private val dbNamespaceName: String,
    private val parameters: Yaml,
    private val follow: Boolean,
) : Runnable {
    companion object {
        private val namespacesAnnotation = ConcurrentHashMap<String, Yaml>()

        fun getNamespaceAnnotation(namespaceName: String): Yaml =
            namespacesAnnotation.computeIfAbsent(namespaceName) {
                val json = ProcessRunner.execute(
                    "kubectl",
                    "get", "ns",
                    "--field-selector", "metadata.name=$namespaceName",
                    "-o",
                    "jsonpath={.items[*].metadata.annotations}"
                )
                if (json.isBlank()) {
                    throw DeploymentException("Could not find namespace $namespaceName")
                }
                val reader = ObjectMapper().reader()
                @Suppress("UNCHECKED_CAST")
                reader.readValue(json, Map::class.java) as Yaml
            }
    }

    private val namespaceAnnotation by lazy {
        getNamespaceAnnotation(namespaceName)
    }

    @Suppress("UNCHECKED_CAST")
    val dbParams by lazy {
        val status = Db.getDbStatus(dbNamespaceName).let {
            if (it == null) {
                println("Starting database $dbNamespaceName...")
                val start = StartDb()
                start.name = dbNamespaceName
                start.run()
                Db.getDbStatus(dbNamespaceName) ?: throw DeploymentException("Can not start DB $dbNamespaceName")
            } else {
                it
            }
        }

        mapOf(
            "username" to status.username,
            "password" to status.password,
            "host" to "db.$dbNamespaceName",
            "db" to status.username
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val configFile = ObjectMapper().writeValueAsString(
            parameters +
                mapOf("dbParams" to dbParams)
        )
        val job = Simulator(
            namespaceAnnotation["kafkaServers"] as String,
            namespaceAnnotation["tag"] as String,
            configFile,
            mapOf(
                "db" to dbNamespaceName,
                "mode" to parameters["simulatorMode"] as String,
            )
        )
        DeployYamls(job.yamls(namespaceName)).run()
        if (follow) {
            followPod(job.app)
        }
    }

    private fun followPod(app: String) {
        val podName = ProcessRunner.execute(
            "kubectl", "get", "pods",
            "-n", namespaceName,
            "--selector=job-name=$app",
            "--output=jsonpath={.items[*].metadata.name}"
        )

        waitForPod(podName)

        ProcessRunner.follow(
            "kubectl",
            "logs",
            "-n", namespaceName,
            podName,
            "-f"
        )
    }

    private fun isRunning(podName: String): Boolean {
        val status = ProcessRunner.execute(
            "kubectl",
            "get", "pods",
            "-n", namespaceName,
            "--field-selector", "metadata.name=$podName",
            "--output=jsonpath={.items[*].status.phase}",
        )

        return when (status) {
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
