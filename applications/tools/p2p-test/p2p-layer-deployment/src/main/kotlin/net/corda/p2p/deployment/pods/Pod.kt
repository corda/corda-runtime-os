package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Namespace
import net.corda.p2p.deployment.Yaml

abstract class Pod {
    abstract val app: String
    abstract val image: String
    open val ports: Collection<Port> = emptyList()
    open val rawData: Collection<RawData<*>> = emptyList()
    open val environmentVariables: Map<String, String> = emptyMap()
    open val hosts: Collection<String>? = null
    open val command: Collection<String>? = null
    open val pullSecrets: Collection<String> = emptyList()

    fun yamls(namespace: Namespace): Collection<Yaml> {
        return rawData.map {
            it.createConfig(namespace.namespaceName, app)
        } +
            createPod(namespace.namespaceName) +
            createService(namespace.namespaceName)
    }

    private fun hostAliases() = if (hosts == null) {
        emptyList()
    } else {
        listOf(
            mapOf(
                "ip" to "0.0.0.0",
                "hostnames" to hosts
            )
        )
    }

    private fun createPod(namespace: String) = mapOf(
        "apiVersion" to "apps/v1",
        "kind" to "Deployment",
        "metadata" to mapOf(
            "name" to app,
            "namespace" to namespace
        ),
        "spec" to mapOf(
            "replicas" to 1,
            "selector" to mapOf("matchLabels" to mapOf("app" to app)),
            "template" to mapOf(
                "metadata" to mapOf("labels" to mapOf("app" to app)),
                "spec" to mapOf(
                    "imagePullSecrets" to pullSecrets.map {
                        mapOf("name" to it)
                    },
                    "containers" to listOf(
                        mapOf(
                            "name" to app,
                            "image" to image,
                            "imagePullPolicy" to "IfNotPresent",
                            "command" to command,
                            "ports" to ports.map {
                                mapOf(
                                    "containerPort" to it.port,
                                    "name" to it.name
                                )
                            },
                            "env" to environmentVariables.map { (key, value) ->
                                mapOf(
                                    "name" to key,
                                    "value" to value
                                )
                            },
                            "volumeMounts" to
                                rawData.map {
                                    it.createVolumeMount(app)
                                }
                        ),
                    ),
                    "volumes" to
                        rawData.map { it.createVolume(app) },
                    "hostAliases" to hostAliases()
                )
            ),
        )
    )

    private fun createService(namespace: String) = if (ports.isEmpty()) {
        emptyList()
    } else {
        listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Service",
                "metadata" to mapOf(
                    "name" to app,
                    "namespace" to namespace,
                    "labels" to mapOf("app" to app)
                ),
                "spec" to mapOf(
                    "type" to "NodePort",
                    "ports" to ports.map {
                        mapOf(
                            "port" to it.port,
                            "name" to it.name
                        )
                    },
                    "selector" to mapOf("app" to app)
                )
            )
        )
    }
}
