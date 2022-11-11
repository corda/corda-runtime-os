package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.cli.CliTask
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlowExecutorE2eTest {

    @Test
    fun `check help page`() {
        val result = CliTask.execute(listOf("initial-rbac", "flow-executor"))
        assertThat(result.exitCode).isNotEqualTo(0)
        assertThat(result.stdOut).isEqualTo("foo")
    }
}