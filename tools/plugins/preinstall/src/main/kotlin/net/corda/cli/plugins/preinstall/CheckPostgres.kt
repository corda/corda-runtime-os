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

        // Get DB credentials from values or secrets
        val username: String
        val password: String
        var bootstrapUsername: String? = null
        var bootstrapPassword: String? = null

        try {
            username = getCredential(yaml.db.cluster.username, namespace)
            password = getCredential(yaml.db.cluster.password, namespace)
            yaml.bootstrap?.db?.cluster?.username?.let { bootstrapUsername = getCredential(it, namespace) }
            yaml.bootstrap?.db?.cluster?.password?.let { bootstrapPassword = getCredential(it, namespace) }
            report.addEntry(PreInstallPlugin.ReportEntry("Get PostgreSQL credentials", true))
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Get PostgreSQL credentials", false, e))
            logger.error(report.failingTests())
            return 1
        }

        // Create the URL using DB host and port
        val postgresUrl = "jdbc:postgresql://${yaml.db.cluster.host}:${yaml.db.cluster.port}/${yaml.db.cluster.database}"
        report.addEntry(PreInstallPlugin.ReportEntry("Create PostgreSQL URL with DB host", true))

        // Try connecting to the DB URL using supplied credentials
        connect(postgresUrl, username, password, "DB")

        // If the bootstrap credentials exist, try connecting to the DB URL using them
        if (bootstrapUsername != null && bootstrapUsername != username && bootstrapPassword != null) {
            connect(postgresUrl, bootstrapUsername!!, bootstrapPassword!!, "bootstrap")
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