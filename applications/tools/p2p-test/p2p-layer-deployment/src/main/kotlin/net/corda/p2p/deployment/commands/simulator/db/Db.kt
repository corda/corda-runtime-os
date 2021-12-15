package net.corda.p2p.deployment.commands.simulator.db

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.p2p.deployment.Yaml
import net.corda.p2p.deployment.commands.MyUserName
import net.corda.p2p.deployment.commands.ProcessRunner
import picocli.CommandLine.Command

@Command(
    subcommands = [
        StartDb::class,
        DbStatus::class,
        Psql::class,
        Jdbc::class,
        StopDb::class,
    ],
    mixinStandardHelpOptions = true,
    name = "db",
    description = ["Manage a database"]
)
class Db {
    data class DbStatus(
        val username: String,
        val password: String,
        val status: String,
    )
    companion object {
        val defaultName = "${MyUserName.userName}-p2p-db".replace('.', '-')

        fun getDbStatus(namespace: String): DbStatus? {
            val statusLine = ProcessRunner.kubeCtlGet(
                "pods",
                listOf(
                    "-n", namespace,
                    "-l", "app=db",
                ),
                listOf("status.phase", "spec.containers[].env")
            ).firstOrNull()
            if ((statusLine == null) || (statusLine.isEmpty())) {
                return null
            }
            val status = statusLine[0]
            val json = statusLine[1]
            val reader = ObjectMapper().reader()
            @Suppress("UNCHECKED_CAST")
            val env = reader.readValue(json, List::class.java) as Collection<Yaml>
            val password = env.firstOrNull {
                it["name"] == "POSTGRES_PASSWORD"
            }?.let {
                it["value"] as? String
            } ?: return null
            val username = env.firstOrNull {
                it["name"] == "POSTGRES_USER"
            }?.let {
                it["value"] as? String
            } ?: return null

            return DbStatus(username, password, status)
        }
    }
}
