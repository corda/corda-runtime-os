package net.corda.cli.plugins.network

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrAndOutNormalized
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine

class MembersListTest {

    companion object {
        private const val DUMMY_VNODE_ID = "10"
    }

    @Test
    fun `command correctly calls HTTP endpoint`() {
        val app = NetworkPluginWrapper.NetworkPlugin()
        app.service = MockHttpService()
        val url = "https://test.r3.com"
        tapSystemErrAndOutNormalized {
            CommandLine(app).execute("--user=test", "--password=test", "-t=$url", "members-list", "-v=$DUMMY_VNODE_ID")
        }.apply {
            assertTrue(contains("$url/members?vnode-id=$DUMMY_VNODE_ID"))
        }
    }
}
