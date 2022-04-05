package net.corda.cli.plugins.network

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrAndOutNormalized
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import picocli.CommandLine

class MembersListTest {

    companion object {
        private const val DUMMY_HOLDING_IDENTITY_ID = "10"
        private const val URL = "https://test.r3.com"

        private lateinit var app: NetworkPluginWrapper.NetworkPlugin

        @BeforeAll
        @JvmStatic
        fun setUp() {
            app = NetworkPluginWrapper.NetworkPlugin()
            app.service = MockHttpService()
        }
    }

    @Test
    fun `command correctly calls HTTP endpoint - without additional params`() {
        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--user=test",
                "--password=test",
                "-t=$URL",
                "members-list",
                "-h=$DUMMY_HOLDING_IDENTITY_ID"
            )
        }.apply {
            assertTrue(contains("$URL/members/$DUMMY_HOLDING_IDENTITY_ID"))
        }
    }

    @Test
    fun `command correctly calls HTTP endpoint - with organisation`() {
        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--user=test",
                "--password=test",
                "-t=$URL",
                "members-list",
                "-h=$DUMMY_HOLDING_IDENTITY_ID",
                "-o=Bob"
            )
        }.apply {
            assertTrue(contains("$URL/members/$DUMMY_HOLDING_IDENTITY_ID?o=Bob"))
        }
    }

    @Test
    fun `command correctly calls HTTP endpoint - with multiple additional params`() {
        tapSystemErrAndOutNormalized {
            CommandLine(app).execute(
                "--user=test",
                "--password=test",
                "-t=$URL",
                "members-list",
                "-h=$DUMMY_HOLDING_IDENTITY_ID",
                "-c=GB",
                "-st=LN",
                "-l=London",
                "-ou=Bob"
            )
        }.apply {
            assertTrue(contains("$URL/members/$DUMMY_HOLDING_IDENTITY_ID?ou=Bob&l=London&st=LN&c=GB"))
        }
    }
}
