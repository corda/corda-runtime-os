package net.corda.e2etest.utilities.config

import com.fasterxml.jackson.databind.JsonNode
import kong.unirest.UnirestException
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.assertWithRetryIgnoringExceptions
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.toJson
import net.corda.rest.ResponseCode.OK
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.fail
import java.io.IOException
import java.time.Duration

/**
 * Return a config manager for a collection of clusters.
 */
fun managedConfig(clusters: Collection<ClusterInfo> = emptyList()): TestConfigManager {
    return if (clusters.size > 1) {
        MultiClusterTestConfigManager(clusters)
    } else {
        SingleClusterTestConfigManager(clusters.firstOrNull() ?: DEFAULT_CLUSTER)
    }
}

fun JsonNode.sourceConfigNode(): JsonNode = this["sourceConfig"].textValue().toJson()

fun JsonNode.configWithDefaultsNode(): JsonNode = this["configWithDefaults"].textValue().toJson()

/**
 * Get the current configuration (as a [JsonNode]) for the specified [section].
 */
fun getConfig(section: String) = DEFAULT_CLUSTER.getConfig(section)

fun ClusterInfo.getConfig(section: String): JsonNode {
    return cluster {
        assertWithRetryIgnoringExceptions {
            command { getConfig(section) }
            condition { it.code == OK.statusCode }
        }.body.toJson()
    }
}

/**
 * This method updates corda config with user provided configuration and call setConfig with user prefered values.
 */
fun ClusterInfo.updateConfig(config: JsonNode, section: String) {
    return cluster {
        val newConfig: JsonNode = config.get("sourceConfig")
        val configVersion: String = config.get("version").toString()
        val schemaMajorVersion: String = config.get("schemaVersion").get("major").toString()
        val schemaMinorVersion: String = config.get("schemaVersion").get("minor").toString()
        setConfig(newConfig, section, configVersion, schemaMajorVersion, schemaMinorVersion)
    }
}

fun setConfig(
    newConfig: JsonNode,
    section: String,
    configVersion: String,
    schemaMajorVersion: String,
    schemaMinorVersion: String
) {
    return cluster {
        try {
            val result = putConfig(
                newConfig.toString(),
                section,
                configVersion,
                schemaMajorVersion,
                schemaMinorVersion
            )

            if (result.code != 202) {
                fail<String>("Config update did not return 202. returned ${result.code} instead. Result ${result.body}")
            }

        } catch (ex: UnirestException) {
            //When a config request is sent with the section set to "corda.messaging" nearly all components in the system will respond to
            // this config change. This will cause the HttpGateway to go down bringing down the HttpServer. This will close all the
            // connections open from clients such as the one used in this test resulting in a UniRestException thrown
            // for the purposes of the test we will this exception and allow the test to proceed.
            // One solution would be for the config endpoint to return a successful result as soon as it receives confirmation the config
            // request is on kafka and to not bother to try return the updated config.
            // https://r3-cev.atlassian.net/browse/CORE-7930
        }
    }
}

internal fun ClusterInfo.updateConfig(config: String, section: String) {
    return cluster {
        val currentConfig = assertWithRetryIgnoringExceptions {
            command { getConfig(section) }
            condition { it.code == OK.statusCode }
        }.body.toJson()
        val currentSchemaVersion = currentConfig["schemaVersion"]

        try {
            val result = putConfig(
                config,
                section,
                currentConfig["version"].toString(),
                currentSchemaVersion["major"].toString(),
                currentSchemaVersion["minor"].toString()
            )

            if (result.code != 202) {
                fail<String>("Config update did not return 202. returned ${result.code} instead. Result ${result.body}")
            }

        } catch (ex: UnirestException) {
            //When a config request is sent with the section set to "corda.messaging" nearly all components in the system will respond to
            // this config change. This will cause the HttpGateway to go down bringing down the HttpServer. This will close all the
            // connections open from clients such as the one used in this test resulting in a UniRestException thrown
            // for the purposes of the test we will this exception and allow the test to proceed.
            // One solution would be for the config endpoint to return a successful result as soon as it receives confirmation the config
            // request is on kafka and to not bother to try return the updated config.
            // https://r3-cev.atlassian.net/browse/CORE-7930
        }
    }
}

/**
 * Wait for the REST API on the rest-worker to respond with an updated config value.
 * If [expectServiceToBeDown] is set to true it is expected the config endpoint will go down before coming back up with the new config.
 */
fun waitForConfigurationChange(
    section: String,
    key: String,
    value: String,
    expectServiceToBeDown: Boolean = true,
    timeout: Duration = Duration.ofMinutes(1)
) = DEFAULT_CLUSTER.waitForConfigurationChange(section, key, value, expectServiceToBeDown, timeout)

fun ClusterInfo.waitForConfigurationChange(
    section: String,
    key: String,
    value: String,
    expectServiceToBeDown: Boolean = true,
    timeout: Duration = Duration.ofMinutes(1)
) {
    cluster {
        if (expectServiceToBeDown) {
            // Wait for the service to become unavailable
            eventually(timeout) {
                assertThatThrownBy {
                    getConfig(section)
                }.hasCauseInstanceOf(IOException::class.java)
            }
        }

        // Wait for the service to become available again and have the expected configuration value
        assertWithRetryIgnoringExceptions {
            timeout(timeout)
            command { getConfig(section) }
            condition {
                val bodyJSON = it.body.toJson()
                it.code == OK.statusCode && bodyJSON["sourceConfig"] != null
                        && bodyJSON.sourceConfigNode()[key] != null && bodyJSON.sourceConfigNode()[key].toString() == value
            }
        }
    }
}