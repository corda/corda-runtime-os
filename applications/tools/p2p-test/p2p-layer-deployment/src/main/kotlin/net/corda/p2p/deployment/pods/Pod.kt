package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.DockerSecrets
import net.corda.p2p.deployment.Yaml

abstract class Pod : Yamlable {
    abstract val app: String
    abstract val image: String
    open val statefulSetReplicas: Int? = null
    open val ports: Collection<Port> = emptyList()
    open val rawData: Collection<RawData<*>> = emptyList()
    open val environmentVariables: Map<String, String> = emptyMap()
    open val labels: Map<String, String> = emptyMap()
    open val hosts: Collection<String>? = null
    open val readyLog: Regex? = null
    open val resourceRequest: ResourceRequest? = null

    override fun yamls(namespaceName: String): Collection<Yaml> {
        return rawData.map {
            it.createConfig(namespaceName, app)
        } +
            createPod(namespaceName) +
            createService(namespaceName)
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
        "kind" to if (statefulSetReplicas == null) "Deployment" else "StatefulSet",
        "metadata" to mapOf(
            "name" to app,
            "namespace" to namespace,
        ),
        "spec" to mapOf(
            "replicas" to (statefulSetReplicas ?: 1),
            "selector" to mapOf("matchLabels" to mapOf("app" to app)),
            "template" to mapOf(
                "metadata" to mapOf(
                    "labels" to mapOf("app" to app) + labels,
                    "annotations" to mapOf(
                        "cluster-autoscaler.kubernetes.io/safe-to-evict" to "false"
                    )
                ),
                "spec" to mapOf(
                    "imagePullSecrets" to listOf(
                        mapOf("name" to DockerSecrets.name)
                    ),
                    "containers" to listOf(
                        mapOf(
                            "name" to app,
                            "image" to image.let { image ->
                                if ((!image.startsWith(DockerSecrets.cordaHost)) &&
                                    (!image.startsWith(DockerSecrets.cacheHost))
                                ) {
                                    if (DockerSecrets.canUseRegistryCache) {
                                        "${DockerSecrets.cacheHost}/$image"
                                    } else {
                                        image
                                    }
                                } else {
                                    image
                                }
                            },
                            "imagePullPolicy" to "IfNotPresent",
                            "ports" to ports.map {
                                mapOf(
                                    "containerPort" to it.port,
                                    "name" to it.displayName
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
                )
            ),
        ) +
            if (statefulSetReplicas == null) {
                emptyMap()
            } else {
                mapOf("serviceName" to app)
            }
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
                    "labels" to mapOf("app" to app) + labels,
                    "annotations" to mapOf(
                        "cluster-autoscaler.kubernetes.io/safe-to-evict" to "false"
                    )
                ),
                "spec" to mapOf(
                    "type" to if (statefulSetReplicas == null) "NodePort" else "ClusterIP",
                    "ports" to ports.map {
                        mapOf(
                            "port" to it.port,
                            "name" to it.displayName
                        )
                    },
                    "selector" to mapOf("app" to app),
                    "clusterIP" to if (statefulSetReplicas == null) null else "None"
                )
            )
        )
    }
}
