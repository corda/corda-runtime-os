package net.corda.cli.plugins.mgm

import java.net.ServerSocket
import kotlin.concurrent.thread

internal object Helpers {
    fun restPasswordFromClusterName(cordaClusterName: String?): String {
        return if (cordaClusterName != null) {
            val getSecret = ProcessBuilder().command(
                "kubectl",
                "get",
                "secret",
                "corda-initial-admin-user",
                "--namespace",
                cordaClusterName,
                "-o",
                "go-template={{ .data.password | base64decode }}"
            ).start()
            if (getSecret.waitFor() != 0) {
                throw BaseOnboard.OnboardException(
                    "Can not get admin password. ${
                    getSecret.errorStream.reader().readText()
                    }"
                )
            }
            getSecret.inputStream.reader().readText()
        } else {
            "admin"
        }
    }

    fun baseUrlFromClusterName(cordaClusterName: String?, restWorkerDeploymentName: String): String {
        val restPort = if (cordaClusterName != null) {
            val port = ServerSocket(0).use {
                it.localPort
            }
            ProcessBuilder().command(
                "kubectl",
                "port-forward",
                "--namespace",
                cordaClusterName,
                "deployment/$restWorkerDeploymentName",
                "$port:8888"
            )
                .inheritIO()
                .start().also { process ->
                    Runtime.getRuntime().addShutdownHook(
                        thread(false) {
                            process.destroy()
                        }
                    )
                }
            Thread.sleep(2000)
            port
        } else {
            8888
        }
        return "https://localhost:$restPort"
    }
    fun urlFromClusterName(cordaClusterName: String?, restWorkerDeploymentName: String): String {
        return "${baseUrlFromClusterName(cordaClusterName, restWorkerDeploymentName)}/api/v1"
    }
}
