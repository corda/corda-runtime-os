package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Namespace
import net.corda.p2p.deployment.Yaml

data class PersistentData(
    val name: String,
    val dir: String,
    val size: String = "5Gi"
) {
    fun createClaim(namespace: Namespace, app: String): Collection<Yaml> {
        val claim = createPersistentVolumeClaim(namespace, app)
        return if (namespace.noVolumes) {
            listOf(claim)
        } else {
            listOf(createPersistentVolume(namespace, app), claim)
        }
    }

    fun createPersistentVolume(
        namespace: Namespace,
        app: String,
    ) = mapOf(
        "kind" to "PersistentVolume",
        "apiVersion" to "v1",
        "metadata" to mapOf(
            "name" to "$app-$name",
            "namespace" to namespace.namespaceName,
            "labels" to mapOf(
                "type" to "local",
                "app" to app
            )
        ),
        "spec" to mapOf(
            "capacity" to mapOf("storage" to size),
            "storageClassName" to namespace.storageClassName,
            "accessModes" to listOf("ReadWriteMany"),
            "hostPath" to mapOf("path" to "/mnt/data/$namespace/$app-$name")
        )
    )

    fun createPersistentVolumeClaim(
        namespace: Namespace,
        app: String,
    ) = mapOf(
        "kind" to "PersistentVolumeClaim",
        "apiVersion" to "v1",
        "metadata" to mapOf(
            "name" to "$app-$name-claim",
            "namespace" to namespace.namespaceName,
            "labels" to mapOf("app" to app)
        ),
        "spec" to mapOf(
            "accessModes" to listOf("ReadWriteMany"),
            "storageClassName" to namespace.storageClassName,
            "resources" to mapOf(
                "requests" to mapOf(
                    "storage" to "5Gi"
                )
            )
        )
    )

    fun createVolumeMount(app: String) = mapOf(
        "mountPath" to dir,
        "name" to "$app-$name"
    )

    fun createVolume(app: String) = mapOf(
        "name" to "$app-$name",
        "persistentVolumeClaim" to mapOf("claimName" to "$app-$name-claim")
    )
}
