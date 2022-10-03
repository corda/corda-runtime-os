package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.JsonNode
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetryIgnoringExceptions
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.httprpc.JsonObject
import net.corda.httprpc.ResponseCode.OK
import net.corda.test.util.eventually
import org.apache.commons.text.StringEscapeUtils
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.io.IOException
import java.time.Duration

private data class TestJsonObject(override val escapedJson: String = "") : JsonObject

fun JsonNode.sourceConfigNode(): JsonNode =
    this["sourceConfig"].textValue().toJson()

fun JsonNode.configWithDefaultsNode(): JsonNode =
    this["configWithDefaults"].textValue().toJson()

/**
 * Get the current configuration (as a [JsonNode]) for the specified [section].
 */
fun getConfig(section: String): JsonNode {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        assertWithRetryIgnoringExceptions {
            command { getConfig(section) }
            condition { it.code == OK.statusCode }
        }.body.toJson()
    }
}

/**
 * Update the cluster configuration with the specified [config] for the requested [section].
 * The currently installed schema and configuration versions are automatically obtained from the running system
 * before updating.
 */
fun updateConfig(config: String, section: String) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        assertWithRetryIgnoringExceptions {
            command {
                val currentConfig = getConfig(section).body.toJson()
                val currentSchemaVersion = currentConfig["schemaVersion"]

                putConfig(
                    TestJsonObject(config),
                    section,
                    currentConfig["version"].toString(),
                    currentSchemaVersion["major"].toString(),
                    currentSchemaVersion["minor"].toString())
            }
            condition { it.code == OK.statusCode }
        }
    }
}

/**
 * Wait for the REST API on the rpc-worker to become unavailable and available again, asserting that the expected
 * configuration change took place.
 */
fun waitForConfigurationChange(section: String, key: String, value: String, timeout: Duration = Duration.ofMinutes(1)) {
    cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        // Wait for the service to become unavailable
        eventually(timeout) {
            assertThatThrownBy {
                getConfig(section)
            }.hasCauseInstanceOf(IOException::class.java)
        }

        // Wait for the service to become available again and have the expected configuration value
        assertWithRetryIgnoringExceptions {
            timeout(timeout)
            command { getConfig(section) }
            condition {
                it.code == OK.statusCode && it.body.toJson().sourceConfigNode()[key].asInt().toString() == value
            }
        }
    }
}
