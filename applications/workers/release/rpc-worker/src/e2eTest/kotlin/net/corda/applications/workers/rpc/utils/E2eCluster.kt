package net.corda.applications.workers.rpc.utils

import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit

interface E2eCluster {
    val members: List<E2eClusterMember>

    val clusterConfig: E2eClusterConfig

    val testToolkit: TestToolkit

    val kafkaTestToolkit: KafkaTestToolKit

    val p2pUrl: String

    fun addMembers(membersToAdd: List<E2eClusterMember>)
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

    override val testToolkit by TestToolkitProperty(
        clusterConfig.rpcHost,
        clusterConfig.rpcPort
    )

    override val p2pUrl get() = "https://${clusterConfig.p2pHost}:${clusterConfig.p2pPort}"

    override val kafkaTestToolkit: KafkaTestToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }

    override fun addMembers(membersToAdd: List<E2eClusterMember>) {
        members.addAll(membersToAdd)
    }
}