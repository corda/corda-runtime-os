package net.corda.sdk.preinstall.checker

import net.corda.sdk.preinstall.data.CordaValues
import net.corda.sdk.preinstall.data.Credentials
import net.corda.sdk.preinstall.data.Database
import net.corda.sdk.preinstall.report.ReportEntry
import java.sql.DriverManager
import java.sql.SQLException

class PostgresChecker(
    yamlFilePath: String,
    private val namespace: String? = null
) : BasePreinstallChecker(yamlFilePath) {

    private fun connect(postgresUrl: String, username: String, password: String, credentialType: String, dbId: String) {
        try {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(postgresUrl, username, password)
            if (connection.isValid(0)) {
                report.addEntry(ReportEntry("Connect to PostgreSQL database $dbId with $credentialType credentials", true))
            } else {
                report.addEntry(
                    ReportEntry(
                        "Connect to PostgreSQL database $dbId with $credentialType credentials",
                        false,
                        Exception("Connection to PostgreSQL database $dbId timed out.")
                    )
                )
            }
        } catch (e: SQLException) {
            report.addEntry(ReportEntry("Connect to PostgreSQL database $dbId with $credentialType credentials", false, e))
        }
    }

    override fun check(): Int {
        val yaml: CordaValues
        try {
            yaml = parseYaml<CordaValues>(yamlFilePath)
            report.addEntry(ReportEntry("Parse PostgreSQL properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse PostgreSQL properties from YAML", false, e))
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
                    report.addEntry(ReportEntry("Get bootstrap PostgreSQL credentials for database ${db.id}", true))
                } catch (e: Exception) {
                    report.addEntry(ReportEntry("Get bootstrap PostgreSQL credentials for database ${db.id}", false, e))
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
        stateManager: Map<String, Credentials>,
        yaml: CordaValues,
        name: String
    ) {
        stateManager.forEach { (type, credentials) ->
            val storage = yaml.stateManager.getValue(type)
            val stateManagerDatabase = yaml.databases.first { it.id == storage.storageId }
            checkConnection(credentials, "worker $name state type $type DB", stateManagerDatabase, storage.storageId)
        }
    }

    private fun checkConnection(
        credentials: Credentials,
        name: String,
        dbConfig: Database,
        dbId: String
    ) {
        try {
            val username = getCredential(credentials.username, namespace)
            val password = getCredential(credentials.password, namespace)
            report.addEntry(ReportEntry("Get PostgreSQL credentials for $name", true))
            val postgresUrl = "jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.name}"
            connect(postgresUrl, username, password, dbId, name)
        } catch (e: Exception) {
            report.addEntry(
                ReportEntry(
                    "Get config DB PostgreSQL credentials for $name",
                    false,
                    e
                )
            )
        }
    }
}
