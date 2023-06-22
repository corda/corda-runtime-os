package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginDb {
    @Test
    fun testDbConfigCreationMissingOptions() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-db-config")
        }
        assertThat(outText).contains("Missing required options: '--name=<connectionName>', '--jdbc-url=<jdbcUrl>', '--user=<username>'")
    }

    @Test
    fun testDbConfigCreationCorda() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-db-config",
                "-n", "connection name",
                "-j", "jdbd:postgres://testurl",
                "--jdbc-pool-max-size", "3",
                "-u", "testuser",
                "-p", "password",
                "-s", "not so secure",
                "-e", "not so secret"
            )
        }
        println(outText)
        assertThat(outText).startsWith(
            "insert into db_connection" +
                " (config, description, connection_id, connection_name, privilege, update_actor, update_ts, version)" +
                " values ('{\"database\":{\"jdbc\":{\"url\":\"jdbd:postgres://testurl\"}," +
                "\"pass\":{\"configSecret\":{\"encryptedSecret\":"
        ).contains(
            "\"}},\"user\":\"testuser\"," +
                    "\"pool\":{" +
                    "\"max_size\":3,\"idleTimeoutSeconds\":120,\"maxLifetimeSeconds\":1800," +
                    "\"keepaliveTimeSeconds\":0,\"validationTimeoutSeconds\":5}}}'," +
                " 'Initial configuration - autogenerated by setup script',"
        ).contains(
            "'connection name'," +
                " 'DML'," +
                " 'Setup Script',"
        ).endsWith("Z', 0)\n")
    }

    @Test
    fun testDbConfigCreationVault() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-db-config",
                "-n", "connection name",
                "-j", "jdbd:postgres://testurl",
                "--jdbc-pool-max-size", "3",
                "-u", "testuser",
                "-v", "myPath",
                "-t", "VAULT",
                "-k", "passwordKey"
            )
        }
        println(outText)
        assertThat(outText).startsWith(
            "insert into db_connection" +
                    " (config, description, connection_id, connection_name, privilege, update_actor, update_ts, version)" +
                    " values ('{\"database\":{\"jdbc\":{\"url\":\"jdbd:postgres://testurl\"}," +
                    "\"pass\":{\"configSecret\":{\"vaultKey\":\"passwordKey\",\"vaultPath\":\"myPath\"}}" +
                    ",\"user\":\"testuser\"," +
                    "\"pool\":{" +
                    "\"max_size\":3,\"idleTimeoutSeconds\":120,\"maxLifetimeSeconds\":1800," +
                    "\"keepaliveTimeSeconds\":0,\"validationTimeoutSeconds\":5}}}'," +
                    " 'Initial configuration - autogenerated by setup script',"
        ).contains(
            "'connection name'," +
                    " 'DML'," +
                    " 'Setup Script',"
        ).endsWith("Z', 0)\n")
    }
}
