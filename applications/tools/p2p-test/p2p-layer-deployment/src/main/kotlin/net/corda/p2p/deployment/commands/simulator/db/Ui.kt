package net.corda.p2p.deployment.commands.simulator.db

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.commands.DeployPods
import net.corda.p2p.deployment.commands.ProcessRunner
import net.corda.p2p.deployment.commands.RunJar
import net.corda.p2p.deployment.pods.SqlPad
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.awt.Desktop
import java.net.URI

@Command(
    name = "ui",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Start GUI to the database"]
)
class Ui : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
    )
    private var namespaceName = Db.defaultName

    private fun uiServiceRunning(): Boolean {
        return ProcessRunner.execute(
            "kubectl",
            "get",
            "service",
            "-n",
            namespaceName,
            "--output",
            "jsonpath={.items[*].metadata.name}",
        ).contains("db-ui")
    }

    private val dbEnv by lazy {
        Db.getDbStatus(namespaceName)
    }

    private val dbService by lazy {
        ProcessRunner.execute(
            "kubectl",
            "get",
            "service",
            "-n",
            namespaceName,
            "-l",
            "app=db",
            "--output",
            "jsonpath={.items[].metadata.name}",
        )
    }

    private fun startUiService() {
        if (!uiServiceRunning()) {
            if (dbEnv == null) {
                throw DeploymentException("Database is not running")
            }
            DeployPods(
                namespaceName,
                listOf(
                    SqlPad(
                        username = dbEnv!!.username,
                        password = dbEnv!!.status,
                        dbHost = "$dbService.$namespaceName",
                        namespace = namespaceName,
                    )
                )
            ).run()
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        startUiService()
        RunJar.startTelepresence()
        val desktop = Desktop.getDesktop()
        desktop.browse(URI("http://db-ui.$namespaceName"))
    }
}
