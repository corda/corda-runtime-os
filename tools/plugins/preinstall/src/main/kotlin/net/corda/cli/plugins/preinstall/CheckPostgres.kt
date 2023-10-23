package net.corda.cli.plugins.preinstall

import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.sql.DriverManager
import java.sql.SQLException
import net.corda.cli.plugins.preinstall.PreInstallPlugin.DB
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "check-postgres",
    description = ["Check that the PostgreSQL DB is up and that the credentials work."],
    mixinStandardHelpOptions = true
)
class CheckPostgres : Callable<Int>, PluginContext() {

    @Parameters(
        index = "0",
        description = ["YAML file containing the username and password values for PostgreSQL - either as values, or as secret references"]
    )
    lateinit var path: String

    @Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for the secrets if there are any"]
    )
    var namespace: String? = null

    private fun connect(postgresUrl: String, username: String, password: String, credentialType: String) {
        try {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(postgresUrl, username, password)
            if (connection.isValid(0)) {
                report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL with $credentialType credentials", true))
            } else {
                report.addEntry(PreInstallPlugin.ReportEntry(
                    "Connect to PostgreSQL with $credentialType credentials",
                    false,
                    Exception("Connection to PostgreSQL DB timed out.")
                ))
            }
        } catch(e: SQLException) {
            report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL with $credentialType credentials", false, e))
        }
    }

    override fun call(): Int {
        val yaml: DB
        try {
            yaml = parseYaml<DB>(path)
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        // Create the URL using DB host and port
        val postgresUrl = "jdbc:postgresql://${yaml.db.cluster.host}:${yaml.db.cluster.port}/${yaml.db.cluster.database}"
        report.addEntry(PreInstallPlugin.ReportEntry("Create PostgreSQL URL with DB host", true))

        if (yaml.bootstrap?.db?.enabled == true) {
            try {
                val bootstrapUsername =
                    getCredential(yaml.db.cluster.username, yaml.bootstrap.db.cluster?.username, namespace)
                val bootstrapPassword =
                    getCredential(yaml.db.cluster.password, yaml.bootstrap.db.cluster?.password, namespace)
                report.addEntry(PreInstallPlugin.ReportEntry("Get bootstrap PostgreSQL credentials", true))
                connect(postgresUrl, bootstrapUsername, bootstrapPassword, "bootstrap")
            } catch (e: Exception) {
                report.addEntry(PreInstallPlugin.ReportEntry("Get bootstrap PostgreSQL credentials", false, e))
            }
        }

        try {
            val username = getCredential(yaml.db.cluster.username, namespace)
            val password = getCredential(yaml.db.cluster.password, namespace)
            report.addEntry(PreInstallPlugin.ReportEntry("Get DB PostgreSQL credentials", true))
            connect(postgresUrl, username, password, "DB")
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Get DB PostgreSQL credentials", false, e))
        }

        return if (report.testsPassed()) {
            logger.info(report.toString())
            0
        } else {
            logger.error(report.failingTests())
            1
        }
    }
}