package net.corda.flow.pipeline

/**
 * The [KillFlowContextProcessor] modifies a given [FlowEventContext] to produce a context that will result in the flow being killed.
 *
 * This is a processor used in exceptional circumstances to kill a flow.
 */
interface KillFlowContextProcessor {
    /**
     * Creates a context for killing a flow given its [FlowEventContext].
     *
     * @param context The [FlowEventContext] that should be modified to kill the flow.
     * @param details Details on why the flow was killed.
     *
     * @return The modified [FlowEventContext]
     */
    fun createKillFlowContext(context: FlowEventContext<Any>, details: Map<String, String>?): FlowEventContext<Any>
}