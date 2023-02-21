package net.corda.cli.plugins.mgm

import net.corda.cli.plugins.common.HttpRpcClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.mgm.Helpers.baseUrlFromClusterName
import net.corda.cli.plugins.mgm.Helpers.rpcPasswordFromClusterName
import net.corda.membership.httprpc.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "allowClientCertificate",
    description = [
        "Allow client certificate in mutual TLS.",
        "This sub command should only be used for internal development"
    ]
)
class AllowClientCertificate : Runnable {
    @Parameters(
        description = ["The name of the k8s namespace with the MGM"],
        paramLabel = "MGM_CLUSTER",
        index = "0"
    )
    lateinit var cordaClusterName: String

    @Parameters(
        description = ["The MGM holding identity short hash"],
        paramLabel = "MGM_HASH",
        index = "1"
    )
    lateinit var mgmShortHash: String

    @Parameters(
        description = ["The certificate subject to allow"],
        paramLabel = "SUBJECTS",
        index = "2..*"
    )
    var subjects: Collection<String> = emptyList()

    @Option(
        names = ["--rpc-worker-deployment-name"],
        description = ["The RPC worker deployment name (default to corda-rpc-worker)"]
    )
    var rpcWorkerDeploymentName: String = "corda-rpc-worker"

    private inner class Command : RestCommand() {
        init {
            targetUrl = baseUrlFromClusterName(cordaClusterName, rpcWorkerDeploymentName)
            password = rpcPasswordFromClusterName(cordaClusterName)
            username = "admin"
        }
    } override fun run() {
        if (subjects.isEmpty()) {
            println("No subjects to allow")
            return
        }

        val command = Command()

        command.createHttpRpcClient(MGMRestResource::class).use { client ->
            println("Allowing certificates...")
            client.start().also { connection ->
                val mgm = connection.proxy
                subjects.forEach { subject ->
                    println("\t Allowing $subject")
                    mgm.mutualTlsAllowClientCertificate(
                        mgmShortHash,
                        subject,
                    )
                }

                println("Success!")
                mgm.mutualTlsListClientCertificate(mgmShortHash).forEach {
                    println("Certificate with subject $it is allowed")
                }
            }
        }
    }
}
