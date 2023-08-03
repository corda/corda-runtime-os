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
 * Runs a function in the scope of a managed configuration which is automatically reverted upon completion.
 */
fun managedConfig(vararg clusters: ClusterInfo, func: (TestConfigManager) -> Unit) {
    val clusterDedup = clusters.toSet()
    if(clusterDedup.size > 1) {
        MultiClusterTestConfigManager(clusterDedup)
    } else {
        SingleClusterTestConfigManager(clusters.getOrNull(0) ?: DEFAULT_CLUSTER)
    }.use(func)
}

fun JsonNode.sourceConfigNode(): JsonNode =
    this["sourceConfig"].textValue().toJson()

fun JsonNode.configWithDefaultsNode(): JsonNode =
    this["configWithDefaults"].textValue().toJson()

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
 * Update the cluster configuration with the specified [config] for the requested [section].
 * The currently installed schema and configuration versions are automatically obtained from the running system
 * before updating.
 */
fun updateConfig(config: String, section: String) = DEFAULT_CLUSTER.updateConfig(config, section)

fun ClusterInfo.updateConfig(config: String, section: String) {
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
                currentSchemaVersion["minor"].toString())

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
                        && bodyJSON.sourceConfigNode()[key] != null
                        && bodyJSON.sourceConfigNode()[key].toString() == value
            }
        }
    }
}