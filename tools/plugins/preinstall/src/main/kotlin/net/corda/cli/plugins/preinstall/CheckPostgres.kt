package net.corda.cli.plugins.preinstall

import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.sql.DriverManager
import java.sql.SQLException
import net.corda.cli.plugins.preinstall.PreInstallPlugin.DB
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext

@CommandLine.Command(name = "check-postgres", description = ["Check that the PostgreSQL DB is up and that the credentials work."])
class CheckPostgres : Runnable, PluginContext(){

    @Parameters(index = "0", description = ["The yaml file containing either the username and password value, " +
            "or valueFrom.secretKeyRef.key fields for Postgres"])
    lateinit var path: String

    @Option(names = ["-n", "--namespace"], description = ["The namespace in which to look for the secrets if there are any"])
    var namespace: String? = null

    @Option(names = ["-v", "--verbose"], description = ["Display additional information when connecting to postgres"])
    var verbose: Boolean = false

    @Option(names = ["-d", "--debug"], description = ["Show extra information while connecting to Postgres for debugging purposes"])
    var debug: Boolean = false

    override fun run() {
        register(verbose, debug)

        val yaml: DB = parseYaml<DB>(path) ?: return

        val username: String = getCredentialOrSecret(yaml.db.cluster.username, namespace) ?: return
        val password: String = getCredentialOrSecret(yaml.db.cluster.password, namespace) ?: return

        val url = "jdbc:postgresql://${yaml.db.cluster.host}:${yaml.db.cluster.port}/postgres"

        try {
            Class.forName("org.postgresql.Driver")
            val connection = DriverManager.getConnection(url, username, password)
            if (connection.isValid(0)) {
                println("[INFO] Postgres credentials found and a DB connection was established.")
            }
        }
        catch(e: SQLException) {
            e.cause?.let{
                log("${e.message} Caused by ${e.cause}", ERROR)
            } ?: run {
                log("${e.message}", ERROR)
            }
        }
    }

}