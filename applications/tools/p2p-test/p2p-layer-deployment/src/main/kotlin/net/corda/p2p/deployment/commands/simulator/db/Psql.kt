package net.corda.p2p.deployment.commands.simulator.db

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.commands.ProcessRunner
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "psql",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Interact with the DB SQL client"]
)
class Psql : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
    )
    private var namespaceName = Db.defaultName

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val name = ProcessRunner.execute(
            "kubectl",
            "get",
            "pod",
            "-n",
            namespaceName,
            "-l",
            "app=db",
            "--output",
            "jsonpath={.items[].metadata.name}",
        )
        if (name.isBlank()) {
            throw DeploymentException("Could not find database pod")
        }

        ProcessRunner.follow(
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
    }
}
