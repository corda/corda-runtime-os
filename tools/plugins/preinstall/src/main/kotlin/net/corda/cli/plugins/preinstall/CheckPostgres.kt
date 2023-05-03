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
    private val logger = getLogger()

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

        val username: String
        val password: String

        try {
            username = getCredential(yaml.db.cluster.username, namespace)
            password = getCredential(yaml.db.cluster.password, namespace)
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Get PostgreSQL credentials", false, e))
            logger.error(report.failingTests())
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
            logger.info(report.toString())
        } else {
            logger.error(report.failingTests())
        }

        return report.testsPassed()
    }
}