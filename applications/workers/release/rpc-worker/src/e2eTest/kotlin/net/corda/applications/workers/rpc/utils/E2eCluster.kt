package net.corda.applications.workers.rpc.utils

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient

interface E2eCluster {
    val members: List<E2eClusterMember>

    val clusterConfig: E2eClusterConfig

    val kafkaTestToolkit: KafkaTestToolKit

    val p2pUrl: String

    fun addMembers(membersToAdd: List<E2eClusterMember>)

    val uniqueName: String

    /**
     * Creates the [HttpRpcClient] for a given [RpcOps] class.
     */
    fun <I : RpcOps> clusterHttpClientFor(
        rpcOpsClass: Class<I>,
        userName: String = AdminPasswordUtil.adminUser
    ): HttpRpcClient<I>
}

internal object E2eClusterFactory {
    fun getE2eCluster(clusterConfig: E2eClusterConfig = E2eClusterAConfig): E2eCluster {
        return E2eClusterImpl(clusterConfig)
    }
}

private class E2eClusterImpl(
    override val clusterConfig: E2eClusterConfig
) : E2eCluster {
    override val members = mutableListOf<E2eClusterMember>()

    private val testToolkit by TestToolkitProperty(
        clusterConfig.rpcHost,
        clusterConfig.rpcPort
    )

    override val p2pUrl get() = "https://${clusterConfig.p2pHost}:${clusterConfig.p2pPort}/gateway"

    override val kafkaTestToolkit: KafkaTestToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }

    override fun addMembers(membersToAdd: List<E2eClusterMember>) {
        members.addAll(membersToAdd)
    }

    override val uniqueName: String
        get() = testToolkit.uniqueName

    override fun <I : RpcOps> clusterHttpClientFor(
        rpcOpsClass: Class<I>,
        userName: String
    ): HttpRpcClient<I> = testToolkit.httpClientFor(rpcOpsClass, userName, clusterConfig.rpcPassword)
}