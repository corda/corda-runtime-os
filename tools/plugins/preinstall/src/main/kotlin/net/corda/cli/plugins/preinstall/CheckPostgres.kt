package net.corda.cli.plugins.preinstall

import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.sql.DriverManager
import java.sql.SQLException
import net.corda.cli.plugins.preinstall.PreInstallPlugin.CordaValues
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

    private fun connect(postgresUrl: String, username: String, password: String, credentialType: String, dbId: String) {
        try {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(postgresUrl, username, password)
            if (connection.isValid(0)) {
                report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL database $dbId with $credentialType credentials", true))
            } else {
                report.addEntry(PreInstallPlugin.ReportEntry(
                    "Connect to PostgreSQL database $dbId with $credentialType credentials",
                    false,
                    Exception("Connection to PostgreSQL database $dbId timed out.")
                ))
            }
        } catch(e: SQLException) {
            report.addEntry(PreInstallPlugin.ReportEntry("Connect to PostgreSQL database $dbId with $credentialType credentials", false, e))
        }
    }

    override fun call(): Int {
        val yaml: CordaValues
        try {
            yaml = parseYaml<CordaValues>(path)
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Parse PostgreSQL properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        if (yaml.bootstrap.db?.enabled == true) {
            yaml.bootstrap.db.databases.forEach { db ->
                var bootstrapUsername: String? = null
                var bootstrapPassword: String? = null
                try {
                    bootstrapUsername = getCredential(db.username, namespace)
                    bootstrapPassword = getCredential(db.password, namespace)
                    report.addEntry(PreInstallPlugin.ReportEntry("Get bootstrap PostgreSQL credentials for database ${db.id}", true))
                } catch (e: Exception) {
                    report.addEntry(PreInstallPlugin.ReportEntry("Get bootstrap PostgreSQL credentials for database ${db.id}", false, e))
                }

                if (bootstrapUsername != null && bootstrapPassword != null) {
                    val dbConfig = yaml.databases.first { it.id == db.id }
                    val postgresUrl = "jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.name}"
                    connect(postgresUrl, bootstrapUsername, bootstrapPassword, "bootstrap", db.id)
                }
            }
        } else {
            // We can only check runtime credentials if not using bootstrap as they won't have been created yet
            val dbConfig = yaml.databases.first { it.id == yaml.config.storageId }
            yaml.workers.forEach { (name, worker) ->
                if (worker.config != null) {
                    checkConnection(worker.config, "worker $name config DB", dbConfig, yaml.config.storageId)
                }
                if (worker.stateManager != null) {
                    checkStateManagerConnections(worker.stateManager, yaml, name)
                }
            }
        }

        return if (report.testsPassed()) {
            logger.info(report.toString())
            0
        } else {
            logger.error(report.failingTests())
            1
        }
    }

    private fun checkStateManagerConnections(
        stateManager: Map<String, PreInstallPlugin.Credentials>,
        yaml: CordaValues,
        name: String
    ) {
        stateManager.forEach { (type, credentials) ->
            val storage = yaml.stateManager[type]!!
            val stateManagerDatabase = yaml.databases.first { it.id == storage.storageId }
            checkConnection(credentials, "worker $name state type $type DB", stateManagerDatabase, storage.storageId)
        }
    }

    private fun checkConnection(
        credentials: PreInstallPlugin.Credentials,
        name: String,
        dbConfig: PreInstallPlugin.Database,
        dbId: String
    ) {
        try {
            val username = getCredential(credentials.username, namespace)
            val password = getCredential(credentials.password, namespace)
            report.addEntry(PreInstallPlugin.ReportEntry("Get PostgreSQL credentials for $name", true))
            val postgresUrl = "jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.name}"
            connect(postgresUrl, username, password, dbId, name)
        } catch (e: Exception) {
            report.addEntry(
                PreInstallPlugin.ReportEntry(
                    "Get config DB PostgreSQL credentials for $name",
                    false,
                    e
                )
            )
        }
    }
}