package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Yaml

abstract class Pod {
    abstract val app: String
    abstract val image: String
    open val ports: Collection<Port> = emptyList()
    open val rawData: Collection<RawData<*>> = emptyList()
    open val environmentVariables: Map<String, String> = emptyMap()
    open val labels: Map<String, String> = emptyMap()
    open val hosts: Collection<String>? = null
    open val pullSecrets: Collection<String> = emptyList()
    open val readyLog: Regex? = null
    open val resourceRequest: ResourceRequest? = null

    fun yamls(namespaceName: String): Collection<Yaml> {
        return rawData.map {
            it.createConfig(namespaceName, app)
        } +
            createPod(namespaceName) +
            createService(namespaceName)
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

    private val resourceRequestYaml by lazy {
        resourceRequest?.let { resourceRequest ->
            val memory = resourceRequest.memory?.let {
                mapOf("memory" to it)
            } ?: emptyMap()
            val cpu = resourceRequest.cpu?.let {
                mapOf("cpu" to it.toString())
            } ?: emptyMap()
            if ((cpu.isEmpty()) && (memory.isEmpty())) {
                emptyMap()
            } else {
                mapOf(
                    "resources" to
                        mapOf(
                            "requests" to
                                memory + cpu
                        )
                )
            }
        } ?: emptyMap()
    }

    private fun createPod(namespace: String) = mapOf(
        "apiVersion" to "apps/v1",
        "kind" to "Deployment",
        "metadata" to mapOf(
            "name" to app,
            "namespace" to namespace,
        ),
        "spec" to mapOf(
            "replicas" to 1,
            "selector" to mapOf("matchLabels" to mapOf("app" to app)),
            "template" to mapOf(
                "metadata" to mapOf("labels" to mapOf("app" to app) + labels),
                "spec" to mapOf(
                    "imagePullSecrets" to pullSecrets.map {
                        mapOf("name" to it)
                    },
                    "containers" to listOf(
                        mapOf(
                            "name" to app,
                            "image" to image,
                            "imagePullPolicy" to "IfNotPresent",
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
                        ) +
                            resourceRequestYaml,
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
                    "labels" to mapOf("app" to app) + labels
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
