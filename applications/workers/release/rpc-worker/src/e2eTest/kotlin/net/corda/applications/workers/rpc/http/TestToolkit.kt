package net.corda.applications.workers.rpc.http

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.messaging.api.records.Record

/**
 * Toolkit for HTTP RPC E2E tests execution
 */
interface TestToolkit {

    /**
     * Creates easily attributable to a testcase unique name
     */
    val uniqueName: String

    /**
     * Creates the [HttpRpcClient] for a given [RpcOps] class.
     */
    fun <I : RpcOps> httpClientFor(rpcOpsClass: Class<I>, userName: String = "admin", password: String = "admin"):
        HttpRpcClient<I>

    fun publishRecordsToKafka(records: Collection<Record<*, *>>)

    fun <K : Any, V : Any> acceptRecordsFromKafka(
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>,
        block: (Record<K, V>) -> Unit
    ): AutoCloseable
}
inline fun <reified K : Any, reified V : Any> TestToolkit.acceptRecordsFromKafka(
    topic: String,
    noinline block: (Record<K, V>) -> Unit
): AutoCloseable {
    return acceptRecordsFromKafka(
        topic,
        K::class.java,
        V::class.java,
        block,
    )
}
