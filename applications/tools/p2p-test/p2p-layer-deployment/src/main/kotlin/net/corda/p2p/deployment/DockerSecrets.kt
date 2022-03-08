package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.Unirest
import java.lang.System.getenv
import java.util.Base64

object DockerSecrets {
    const val name = "p2p-docker-secret"
    const val cordaHost = "corda-os-docker.software.r3.com"
    val canUseRegistryCache by lazy {
        Unirest.get("https://docker-remotes.software.r3.com/v2/library/alpine/tags/list")
            .basicAuth(
                getenv("CORDA_ARTIFACTORY_USERNAME"),
                getenv("CORDA_ARTIFACTORY_PASSWORD"),
            ).asString().isSuccess.also {
                if (it) {
                    println("Using R3 docker registry cache to access docker images.")
                } else {
                    println("Can not access R3 docker registry cache to access docker images. Will use docker hub directly.")
                }
            }
    }
    const val cacheHost = "docker-remotes.software.r3.com"
    fun secret(namespace: String) = mapOf(
        "apiVersion" to "v1",
        "kind" to "Secret",
        "metadata" to mapOf(
            "name" to name,
            "namespace" to namespace
        ),
        "type" to "kubernetes.io/dockerconfigjson",
        "data" to mapOf(
            ".dockerconfigjson" to base64JsonConfig
        )
    )

    private val base64JsonConfig by lazy {
        Base64.getEncoder().encodeToString(jsonConfig.toByteArray())
    }

    private val jsonConfig by lazy {
        ObjectMapper().writeValueAsString(config)
    }
    private val config by lazy {
        val cache = if (canUseRegistryCache) {
            mapOf(
                cacheHost to mapOf(
                    "auth" to base64auth
                )
            )
        } else {
            emptyMap()
        }

        val corda = mapOf(
            cordaHost to mapOf(
                "auth" to base64auth
            )
        )

        mapOf(
            "auths" to
                cache + corda
        )
    }

    private val base64auth by lazy {
        Base64.getEncoder().encodeToString(auth.toByteArray())
    }

    private val auth by lazy {
        "${getAndCheckEnv("CORDA_ARTIFACTORY_USERNAME")}:${getAndCheckEnv("CORDA_ARTIFACTORY_PASSWORD")}"
    }
}
