package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.ConfigFactory
import net.corda.applications.workers.workercommon.StateManagerConfigHelper
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StateManagerConfigHelperTest {

    @Test
    fun `create config throws illegal argument if no cluster db is configured`() {
        assertThrows<IllegalArgumentException> {
            StateManagerConfigHelper.createStateManagerConfigFromClusterDb(
                ConfigFactory.parseMap(
                    mapOf(
                        "a" to "b"
                    )
                )
            )
        }
    }

    @Test
    fun `create config using cluster database as fallback`() {
        val result = StateManagerConfigHelper.createStateManagerConfigFromClusterDb(
            ConfigFactory.parseMap(
                mapOf(
                    "db.database.jdbc.url" to "url",
                    "db.database.user" to "user",
                    "db.database.pass" to "pass"
                )
            )
        )

        assertThat(result.hasPath(BootConfig.BOOT_STATE_MANAGER))
        val smConfig = result.getConfig(BootConfig.BOOT_STATE_MANAGER)
        SoftAssertions.assertSoftly { softly ->
            StateManagerConfig.StateType.values().map { type ->
                assertStateType(softly, smConfig, type,
                    url = "url", user = "user", pass = "pass")
            }
        }
    }

    @Test
    fun `create config for all state types using cli parameters`() {
        val result = StateManagerConfigHelper.createStateManagerConfigFromCli(
            mapOf(
                "database.jdbc.url" to "url",
                "database.user" to "user",
                "database.pass" to "pass",
            )
        )

        assertThat(result.hasPath(BootConfig.BOOT_STATE_MANAGER))
        val smConfig = result.getConfig(BootConfig.BOOT_STATE_MANAGER)
        SoftAssertions.assertSoftly { softly ->
            StateManagerConfig.StateType.values().map { type ->
                assertStateType(softly, smConfig, type, url = "url", user = "user", pass = "pass")
            }
        }
    }


}