package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Yaml

abstract class Job {
    abstract val app: String
    abstract val image: String
    open val rawData: Collection<RawData<*>> = emptyList()
    open val environmentVariables: Map<String, String> = emptyMap()
    open val command: Collection<String>? = null
    open val pullSecrets: Collection<String> = emptyList()
    open val labels: Map<String, String> = emptyMap()

    fun yamls(namespaceName: String): Collection<Yaml> {
        return rawData.map {
            it.createConfig(namespaceName, app)
        } +
            createJob(namespaceName)
    }

    private fun createJob(namespace: String) = mapOf(
        "apiVersion" to "batch/v1",
        "kind" to "Job",
        "metadata" to mapOf(
            "name" to app,
            "namespace" to namespace
        ),
        "spec" to mapOf(
            "backoffLimit" to 1,
            "template" to mapOf(
                "metadata" to mapOf(
                    "labels" to
                        mapOf("app" to app) + labels,
                    "annotations" to mapOf(
                        "cluster-autoscaler.kubernetes.io/safe-to-evict" to "false"
                    )
                ),
                "spec" to mapOf(
                    "imagePullSecrets" to pullSecrets.map {
                        mapOf("name" to it)
                    },
                    "restartPolicy" to "Never",
                    "containers" to listOf(
                        mapOf(
                            "name" to app,
                            "image" to image,
                            "imagePullPolicy" to "IfNotPresent",
                            "command" to command,
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
                )
            ),
        )
    )
}
