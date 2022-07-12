package net.corda.applications.workers.smoketest

import net.corda.applications.workers.smoketest.virtualnode.helpers.ClusterBuilder
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigTests {
    @Test
    fun `can update config`() {
        cluster {
            val cb = this
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val existing = getConfig(this, "corda.reconciliation")

            val payload = getReconConfig(getConfigVersion(existing.body))

            val response = assertWithRetry {
                command {
                    putConfig(cb, payload)
                }
                condition { it.code == 200 }
            }

            val expectedConfig = getConfigValue(payload.toJson()["request"].toString())
            val actualConfig = getConfigValue(response.body)
            assertThat(actualConfig).isEqualTo(expectedConfig)
        }
    }

    @Test
    fun `get config includes defaults`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val existing = getConfig(this, "corda.reconciliation")

            val sourceConfigValues = existing.body.toJson()["sourceConfig"].textValue().toJson().count()
            val defaultedConfigValues = existing.body.toJson()["configWithDefaults"].textValue().toJson().count()
            assertThat(defaultedConfigValues).isGreaterThan(sourceConfigValues)
        }
    }

    private fun getReconConfig(version: String) = """
    {
      "request": {
        "config": "{\"configIntervalMs\":15000}",
        "schemaVersion": {
          "major": 1,
          "minor": 0
        },
        "section": "corda.reconciliation",
        "version": $version
      }
    }
    """.trimIndent()

    private fun getConfigVersion(body: String) = body.toJson()["version"].toString()

    private fun getConfigValue(body: String) = body.toJson()["config"].toString()

    private fun getConfig(builder: ClusterBuilder, section: String) =
        builder.get("/api/v1/config/$section")


    private fun putConfig(builder: ClusterBuilder, config: String) =
        builder.put("/api/v1/config", config)
}