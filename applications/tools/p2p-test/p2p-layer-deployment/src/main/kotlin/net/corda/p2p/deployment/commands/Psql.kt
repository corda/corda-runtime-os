package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "psql",
    showDefaultValues = true,
    description = ["Interact with the DB SQL client"]
)
class Psql : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true,
    )
    private lateinit var namespaceName: String

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val getPod = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n",
            namespaceName,
            "-l",
            "app=db",
            "--output",
            "jsonpath={.items[].metadata.name}",
        ).start()
        if (getPod.waitFor() != 0) {
            System.err.println(getPod.errorStream.reader().readText())
            throw DeploymentException("Could not get pods")
        }
        val name = getPod
            .inputStream
            .reader()
            .readText()
        if (name.isBlank()) {
            throw DeploymentException("Could not find database pod")
        }

        val bash = ProcessBuilder().command(
            "kubectl",
            "exec",
            "-it",
            "-n",
            namespaceName,
            name,
            "--",
            "psql",
            "-U",
            "corda",
        )
            .inheritIO()
            .start()

        bash.waitFor()
    }
}
