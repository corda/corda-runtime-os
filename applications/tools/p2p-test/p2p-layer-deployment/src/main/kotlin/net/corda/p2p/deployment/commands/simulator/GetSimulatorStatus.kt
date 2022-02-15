package net.corda.p2p.deployment.commands.simulator

import net.corda.p2p.deployment.commands.ProcessRunner
import java.time.Instant

data class JobStatus(
    val name: String,
    val db: String,
    val completedAt: Instant?
) {
    fun display(withDb: Boolean): String {
        val db = if (withDb) {
            " report to $db"
        } else {
            ""
        }
        val time = if (completedAt != null) {
            " finished at $completedAt"
        } else {
            " running"
        }
        return "$name$db$time"
    }
}
class GetSimulatorStatus(
    private val mode: String,
    private val namespaceName: String
) : () -> Collection<JobStatus> {

    override fun invoke(): Collection<JobStatus> {
        return ProcessRunner.kubeCtlGet(
            "job",
            listOf(
                "-n", namespaceName,
                "-l", "mode=$mode"
            ),
            listOf(
                "metadata.name",
                "metadata.labels.db",
                "status.completionTime",
            )
        ).map {
            val completedAt = if (it[2].isNotBlank()) {
                Instant.parse(it[2])
            } else {
                null
            }
            JobStatus(it[0], it[1], completedAt)
        }.toList()
    }
}
