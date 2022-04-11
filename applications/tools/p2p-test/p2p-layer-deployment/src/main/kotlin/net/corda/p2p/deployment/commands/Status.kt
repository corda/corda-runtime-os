package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.Yaml
import net.corda.p2p.deployment.commands.simulator.GetSimulatorStatus
import net.corda.p2p.deployment.commands.simulator.db.Db
import picocli.CommandLine.Command
import java.time.Duration
import java.time.Instant

@Command(
    name = "status",
    showDefaultValues = true,
    description = ["print the status of all the known namespaces"],
    mixinStandardHelpOptions = true,
    usageHelpAutoWidth = true,
)
class Status : Runnable {
    override fun run() {
        val now = Instant.now()
        val nameSpaces = ProcessRunner.execute(
            "kubectl", "get", "ns",
            "-l", "namespace-type=p2p-deployment,p2p-namespace-type=deployed-layer",
            "-o",
            "jsonpath={range .items[*]}" +
                "{.metadata.name}{\"\\t\"}" +
                "{.metadata.annotations.host}{\"\\t\"}" +
                "{.metadata.annotations.debug}{\"\\t\"}" +
                "{.metadata.annotations.group-id}{\"\\t\"}" +
                "{.metadata.creationTimestamp}{\"\\t\"}" +
                "{.metadata.labels.creator}{\"\\t\"}" +
                "{.metadata.annotations.x500-name}{\"\\n\"}" +
                "{end}"
        ).lines().map {
            it.split("\t")
        }.filter {
            it.size > 6
        }
            .associate {
                val duration = Duration.between(Instant.parse(it[4]), now)
                it[0] to mapOf(
                    "creator" to it[5],
                    "runningFor" to "${duration.toHours()}:${duration.toMinutesPart()}:${duration.toSecondsPart()}",
                    "host" to it[1],
                    "x500Name" to it[6],
                    "group-id" to it[3],
                    "debug" to it[2].toBoolean(),
                )
            }.mapValues { (name, data) ->

                data + NamespaceStatusGetter(name).getInfo()
            }

        val dbs = ProcessRunner.execute(
            "kubectl", "get", "ns",
            "-l", "namespace-type=p2p-deployment,p2p-namespace-type=db",
            "-o",
            "jsonpath={range .items[*]}" +
                "{.metadata.name}{\"\\t\"}" +
                "{.metadata.creationTimestamp}{\"\\t\"}" +
                "{.metadata.labels.creator}{\"\\n\"}" +
                "{end}"
        ).lines().map {
            it.split("\t")
        }.filter {
            it.size > 2
        }
            .associate {
                val duration = Duration.between(Instant.parse(it[1]), now)
                it[0] to mapOf(
                    "creator" to it[2],
                    "runningFor" to "${duration.toHours()}:${duration.toMinutesPart()}:${duration.toSecondsPart()}",
                )
            }.mapValues { (name, data) ->
                val status = Db.getDbStatus(name)
                if (status != null) {
                    data + mapOf(
                        "status" to status.status,
                        "username" to status.username,
                        "password" to status.password,
                    )
                } else {
                    data + mapOf(
                        "status" to "missing",
                    )
                }
            }

        val writer = ObjectMapper(YAMLFactory()).writer()

        println(
            writer.writeValueAsString(
                mapOf(
                    "deployments" to nameSpaces,
                    "databases" to dbs
                )
            )
        )
    }

    private class NamespaceStatusGetter(private val namespace: String) {
        fun getInfo(): Yaml {
            return mapOf(
                "kafkaBrokers" to listPods("kafka-broker"),
                "gateways" to listPods("p2p-gateway"),
                "link-managers" to listPods("p2p-link-manager"),
                "senders" to listSimulators("SENDER", true),
                "receivers" to listSimulators("RECEIVER", false),
                "db-sinks" to listSimulators("DB_SINK", true),
            )
        }
        private fun listPods(type: String): Collection<String> {
            return ProcessRunner.execute(
                "kubectl",
                "get",
                "pods",
                "-n", namespace,
                "-l", "type=$type",
                "--output=jsonpath={.items[*].metadata.name}"
            ).split(" ")
        }
        private fun listSimulators(mode: String, showDb: Boolean): Collection<Yaml> {
            return GetSimulatorStatus(mode, namespace)().map {
                mapOf(
                    "name" to it.name
                ) + if (showDb) {
                    mapOf("db" to it.db)
                } else emptyMap()
            }
        }
    }
}
