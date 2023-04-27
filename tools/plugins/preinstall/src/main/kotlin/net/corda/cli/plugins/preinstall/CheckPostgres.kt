package net.corda.cli.plugins.preinstall

import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.sql.DriverManager
import java.sql.SQLException
import net.corda.cli.plugins.preinstall.PreInstallPlugin.DB
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import java.util.concurrent.Callable

@CommandLine.Command(name = "check-postgres", description = ["Check that the PostgreSQL DB is up and that the credentials work."]
)
class CheckPostgres : Callable<Int>, PluginContext() {

    @Parameters(
        index = "0",
        description = ["YAML file containing either the username and password value, or valueFrom.secretKeyRef.key fields for PostgreSQL"]
    )
    lateinit var path: String

    @Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for the secrets if there are any"]
    )
    var namespace: String? = null

    @Option(
        names = ["-u", "--url"],
        description = ["The kubernetes cluster URL (if the preinstall is being called from outside the cluster)"]
    )
    var url: String? = null

    @Option(
        names = ["-v", "--verbose"],
        description = ["Display additional information when connecting to postgres"]
    )
    var verbose: Boolean = false

    @Option(
        names = ["-d", "--debug"],
        description = ["Show extra information while connecting to PostgreSQL for debugging purposes"]
    )
    var debug: Boolean = false

    override fun call(): Int {
        register(verbose, debug)

        val yaml: DB
        try {
            yaml = parseYaml<DB>(path)
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", false, e))
            getLogger().error(report.failingTests())
            return 1
        }

        val username: String
        val password: String

        try {
            username = getCredentialOrSecret(yaml.db.cluster.username, namespace, url)
            password = getCredentialOrSecret(yaml.db.cluster.password, namespace, url)
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Get PostgreSQL credentials", false, e))
            getLogger().error(report.failingTests())
            return 1
        }

        val postgresUrl = "jdbc:postgresql://${yaml.db.cluster.host}:${yaml.db.cluster.port}/postgres"

        try {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(postgresUrl, username, password)
            if (connection.isValid(0)) {
                report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL", true))
            }
        } catch(e: SQLException) {
            report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL", false, e))
        }

        if (report.testsPassed() == 0) {
            getLogger().info(report.toString())
        } else {
            getLogger().error(report.failingTests())
        }

        return report.testsPassed()
    }
}