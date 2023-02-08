package net.corda.applications.workers.rest

import net.corda.applications.workers.rest.cli.CliTask
import net.corda.applications.workers.rest.http.SkipWhenRestEndpointUnavailable
import net.corda.applications.workers.rest.http.TestToolkitProperty
import net.corda.applications.workers.rest.http.TestToolkitProperty.Companion.DEFAULT_HTTP_HOST
import net.corda.applications.workers.rest.http.TestToolkitProperty.Companion.DEFAULT_HTTP_PORT
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rest.utils.AdminPasswordUtil.adminUser
import net.corda.libs.permissions.endpoints.v1.role.RoleEndpoint
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class FlowExecutorE2eTest {

    private val testToolkit by TestToolkitProperty()

    @Test
    fun `check help page`() {
        val result = CliTask.execute(listOf("initial-rbac", "flow-executor"))
        assertThat(result.exitCode).isNotEqualTo(0)
        assertThat(result.stdErr).contains("Missing required options").contains("FlowExecutorRole")
    }

    @Test
    @SkipWhenRestEndpointUnavailable
    fun `check FlowExecutor role creation`() {
        val randomVNodeHash = UUID.randomUUID().toString().replace("-", "").take(12)

        val result = CliTask.execute(listOf("initial-rbac", "flow-executor", "-u", adminUser, "-p", adminPassword,
            "-t", "https://$DEFAULT_HTTP_HOST:$DEFAULT_HTTP_PORT", "-v", randomVNodeHash))
        assertThat(result.exitCode).withFailMessage(result.stdErr).isEqualTo(0)
        val roleName = "FlowExecutorRole-$randomVNodeHash"
        assertThat(result.stdOut).contains("Successfully created $roleName")

        // Independently check that the role is available with permissions as expected
        testToolkit.httpClientFor(RoleEndpoint::class.java).use { client ->
            val proxy = client.start().proxy
            eventually(duration = 30.seconds, waitBetween = 1.seconds) {
                val roleEntity = assertDoesNotThrow { proxy.getRoles().single { it.roleName == roleName } }
                assertThat(roleEntity.permissions).hasSize(6)
            }
        }
    }
}