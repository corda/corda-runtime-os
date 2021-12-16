package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.System.getenv
import java.util.Base64

object CordaOsDockerDevSecret {
    const val name = "corda-os-docker-secret"
    const val host = "corda-os-docker.software.r3.com"
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
        mapOf(
            "auths" to
                mapOf(
                    host to mapOf(
                        "auth" to base64auth
                    )
                )
        )
    }

    private val base64auth by lazy {
        Base64.getEncoder().encodeToString(auth.toByteArray())
    }

    private val auth by lazy {
        "${getenv("CORDA_ARTIFACTORY_USERNAME")}:${getenv("CORDA_ARTIFACTORY_PASSWORD")}"
    }
}
