package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException

class KafkaSetup(
    private val namespaceName: String
) : Runnable {
    override fun run() {
        println("Setting up kafka topics...")
        println("\t p2p.out.markers...")
        createOrAlterTopic("p2p.out.markers")
        listOf("ConfigTopic", "session.out.partitions", "p2p.out.markers.state").forEach {
            println("\t $it...")
            createOrAlterTopic(it)
            makeCompactTopic(it)
        }
    }

    private fun makeCompactTopic(topicName: String) {
        execute(
            "--alter",
            "--topic",
            topicName,
            "--config",
            "cleanup.policy=compact",
            "--config",
            "segment.ms=300000",
            "--config",
            "delete.retention.ms=300000",
            "--config",
            "min.compaction.lag.ms=300000",
            "--config",
            "min.cleanable.dirty.ratio=0.8",
        )
    }

    private fun createOrAlterTopic(name: String) {
        try {
            createTopic(name)
        } catch (e: DeploymentException) {
            try {
                println("\t Topic $name already exists")
                alterTopic(name)
            } catch (e: DeploymentException) {
                println("\t Topic $name already exists and setup")
            }
        }
    }

    private fun alterTopic(name: String) {
        execute(
            "--alter",
            "--topic",
            name,
            "--partitions",
            "10",
        )
    }
    private fun createTopic(name: String) {
        execute(
            "--create",
            "--topic",
            name,
            "--partitions",
            "10",
            "--replication-factor",
            "3",
        )
    }

    private fun execute(vararg args: String): Collection<String> {
        val commandInPod = "\$KAFKA_HOME/bin/kafka-topics.sh --zookeeper=\$KAFKA_ZOOKEEPER_CONNECT ${args.joinToString(" ")}"

        val bash = ProcessBuilder().command(
            listOf(
                "kubectl",
                "exec",
                "-n",
                namespaceName,
                pod,
                "--",
                "bash",
                "-c",
                commandInPod
            )
        )
            .start()

        if (bash.waitFor() != 0) {
            val error = bash.errorStream.reader().readText()
            throw DeploymentException("Can not run $commandInPod: $error")
        }
        return bash.inputStream.reader().readLines()
    }

    @Suppress("UNCHECKED_CAST")
    private val pod by lazy {
        val getPods = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "-l", "app=kafka-broker-1",
            "-o", "jsonpath={.items[*].metadata.name}"
        ).start()
        if (getPods.waitFor() != 0) {
            System.err.println(getPods.errorStream.reader().readText())
            throw DeploymentException("Could not get pods")
        }

        getPods.inputStream.reader().readText().trim()
    }
}
