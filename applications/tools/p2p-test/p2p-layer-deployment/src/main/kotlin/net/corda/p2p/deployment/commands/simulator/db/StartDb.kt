package net.corda.p2p.deployment.commands.simulator.db

import net.corda.p2p.deployment.DockerSecrets
import net.corda.p2p.deployment.commands.DeployPods
import net.corda.p2p.deployment.commands.DeployYamls
import net.corda.p2p.deployment.commands.Destroy
import net.corda.p2p.deployment.commands.MyUserName
import net.corda.p2p.deployment.pods.DbDetails
import net.corda.p2p.deployment.pods.PostGreSql
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "start",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    description = ["Start a database"]
)
class StartDb : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the database namespace"],
    )
    var name = Db.defaultName

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

    private val nameSpaceYaml by lazy {
        mapOf(
            "apiVersion" to "v1",
            "kind" to "Namespace",
            "metadata" to mapOf(
                "name" to name,
                "labels" to mapOf(
                    "namespace-type" to "p2p-deployment",
                    "p2p-namespace-type" to "db",
                    "creator" to MyUserName.userName,
                ),
                "annotations" to mapOf(
                    "type" to "p2p",
                )
            )
        )
    }

    override fun run() {
        val delete = Destroy()
        delete.namespaceName = name
        delete.run()

        DeployYamls(
            listOf(
                nameSpaceYaml,
                DockerSecrets.secret(name)
            )
        ).run()
        val dbDetails = DbDetails(
            dbUsername,
            dbPassword,
            sqlInitFile
        )
        DeployPods(name, listOf(PostGreSql(dbDetails))).run()
    }
}
