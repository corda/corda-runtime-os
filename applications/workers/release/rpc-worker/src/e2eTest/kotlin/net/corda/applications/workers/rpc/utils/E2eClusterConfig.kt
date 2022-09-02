package net.corda.applications.workers.rpc.utils

import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

abstract class E2eClusterConfig {
    private companion object {
        private const val DEFAULT_RPC_PORT = 8888
        private const val DEFAULT_P2P_PORT = 8080
    }

    abstract val clusterName: String

    val rpcHost = "localhost"

    /*: String
        get() = "corda-rpc-worker.$clusterName"*/
    val rpcPort by lazy {
        val port = ServerSocket(0).use {
            it.localPort
        }
        println("for $clusterName - will use port $port")
        val output = File.createTempFile("forward", "txt")
        val err = File.createTempFile("forward", "err")
        val process = ProcessBuilder(
            "kubectl",
            "port-forward",
            "--namespace",
            clusterName,
            "deployment/corda-rpc-worker",
            "$port:$DEFAULT_RPC_PORT"
        )
            .redirectOutput(output)
            .redirectError(err)
            .start()
        println("QQQ process -> $process - logs -> $output, err -> $err")
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                println("Killing forward for $clusterName ($process)")
                if(process.isAlive) {
                    process.destroyForcibly()
                }
            }
        )

        Thread.sleep(3000)
        port
    }
    val p2pHost: String
        get() = "corda-p2p-gateway-worker.$clusterName"
    val p2pPort = DEFAULT_P2P_PORT
}

internal object E2eClusterAConfig : E2eClusterConfig() {
    override val clusterName = "yift-cluster-a"
}

internal object E2eClusterBConfig : E2eClusterConfig() {
    override val clusterName = "yift-cluster-b"
}

internal object E2eClusterCConfig : E2eClusterConfig() {
    override val clusterName = "yift-cluster-mgm"
}