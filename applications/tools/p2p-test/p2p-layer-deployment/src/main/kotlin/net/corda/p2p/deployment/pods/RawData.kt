package net.corda.p2p.deployment.pods
interface RawFile

abstract class RawData<T : RawFile>(
    val name: String,
    val dirName: String,
    val content: Collection<T>
) {
    abstract val data: Map<String, Map<String, String>>
    fun createConfig(
        namespace: String,
        app: String,
    ) = mapOf(
        "apiVersion" to "v1",
        "kind" to "ConfigMap",
        "metadata" to mapOf(
            "namespace" to namespace,
            "name" to "$app-$name-config",
            "labels" to mapOf("app" to app)
        )
    ) + data

    fun createVolume(
        app: String,
    ) = mapOf(
        "name" to "$app-$name-config",
        "configMap" to mapOf("name" to "$app-$name-config")
    )

    fun createVolumeMount(
        app: String,
    ) = mapOf(
        "name" to "$app-$name-config",
        "mountPath" to dirName
    )
}
