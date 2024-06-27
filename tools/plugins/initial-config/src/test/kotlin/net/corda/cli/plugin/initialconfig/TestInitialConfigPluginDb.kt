package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginDb {
    @Test
    fun testDbConfigCreationMissingOptions() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin()

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
        val app = InitialConfigPlugin()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-db-config",
                "-n", "connection name",
                "-j", "jdbd:postgres://testurl",
                "--jdbc-pool-max-size", "3",
                "--idle-timeout", "121",
                "--max-lifetime", "1801",
                "--keepalive-time", "1",
                "--validation-timeout", "6",
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
                    "\"max_size\":3,\"idleTimeoutSeconds\":121,\"maxLifetimeSeconds\":1801," +
                    "\"keepaliveTimeSeconds\":1,\"validationTimeoutSeconds\":6}}}'," +
                " 'Initial configuration - autogenerated by setup script',"
        ).contains(
            "'connection name'," +
                " 'DML'," +
                " 'Setup Script',"
        ).endsWith("Z', 0);\n")
    }

    @Test
    fun `test DbConfig creation with MinPoolSize`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-db-config",
                "-n", "connection name",
                "-j", "jdbd:postgres://testurl",
                "--jdbc-pool-max-size", "3",
                "--jdbc-pool-min-size", "1",
                "--idle-timeout", "121",
                "--max-lifetime", "1801",
                "--keepalive-time", "1",
                "--validation-timeout", "6",
                "-u", "testuser",
                "-p", "password",
                "-s", "not so secure",
                "-e", "not so secret"
            )
        }
        println(outText)
        assertThat(outText).contains(
            "\"pool\":{" +
                    "\"max_size\":3,\"min_size\":1," +
                    "\"idleTimeoutSeconds\":121,\"maxLifetimeSeconds\":1801," +
                    "\"keepaliveTimeSeconds\":1,\"validationTimeoutSeconds\":6}}}',"
        )
    }

    @Test
    fun testDbConfigCreationVault() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin()

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
        ).endsWith("Z', 0);\n")
    }

    // Running the command via command line applies additional escaping
    @Test
    fun `test DbConfig creation via command line with escaped string`() {
        val app = InitialConfigPlugin()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).execute(
                "create-db-config",
                "-n", "connection name",
                "-j", "jdbd:postgres://test\"url",
                "--jdbc-pool-max-size", "3",
                "--jdbc-pool-min-size", "1",
                "--idle-timeout", "121",
                "--max-lifetime", "1801",
                "--keepalive-time", "1",
                "--validation-timeout", "6",
                "-u", "test\"user",
                "-p", "password",
                "-s", "not so secure",
                "-e", "not so secret")
        }
        assertThat(outText).contains("\"user\":\"test\\\"user\"")
    }
}
