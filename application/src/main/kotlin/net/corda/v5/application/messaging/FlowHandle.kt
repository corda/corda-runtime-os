package net.corda.v5.application.messaging

import net.corda.v5.application.flows.FlowId
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import java.util.concurrent.CompletableFuture

/**
 * [FlowHandle] is a serialisable handle for the started flow, parameterised by the type of the flow's return value.
 */
@DoNotImplement
@CordaSerializable
interface FlowHandle<A> : AutoCloseable {
    /**
     * The started flow's Id.
     */
    val id: FlowId

    /**
     * A [CompletableFuture] of the flow's return value.
     */
    val returnValue: CompletableFuture<A>

    /**
     * Use this function for flows whose returnValue is not going to be used, so as to free up server resources.
     */
    override fun close()
}