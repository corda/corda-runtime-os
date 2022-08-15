package net.corda.applications.workers.rpc.utils

import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit

data class ClusterTestData(
    val testToolkit: TestToolkit,
    val p2pHost: String,
    val p2pPort: Int,
    val members: List<MemberTestData>
) {
    val p2pUrl get() = "https://$p2pHost:$p2pPort"
    val kafkaTestToolkit: KafkaTestToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }
}