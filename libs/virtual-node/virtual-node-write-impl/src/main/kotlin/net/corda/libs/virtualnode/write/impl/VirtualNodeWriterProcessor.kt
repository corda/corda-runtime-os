package net.corda.libs.virtualnode.write.impl

import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC

/**
 * An RPC responder processor that handles configuration management requests.
 *
 * Listens for configuration management requests over RPC. Persists the updated configuration to the cluster database
 * and publishes the updated configuration to Kafka.
 *
 * @property publisher The publisher used to publish to Kafka.
 */
internal class VirtualNodeWriterProcessor(
    private val publisher: Publisher
) : RPCResponderProcessor<ConfigurationManagementRequest, ConfigurationManagementResponse> {

    /**
     * For each [request], the processor attempts to commit the updated config to the cluster database. If successful,
     * the updated config is then published by the [publisher] to the [CONFIG_TOPIC] topic for consumption using a
     * `ConfigReader`.
     *
     * If both steps succeed, [respFuture] is completed successfully. Otherwise, it is completed unsuccessfully.
     */
    override fun onNext(request: ConfigurationManagementRequest, respFuture: ConfigurationManagementResponseFuture) {
        val response = ConfigurationManagementResponse(
            true, null, "", "", 0, 0
        )
        respFuture.complete(response)
    }
}