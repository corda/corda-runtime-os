package net.corda.v5.application.persistence.query

import net.corda.v5.base.annotations.DoNotImplement
import java.util.stream.Stream

/**
 * Base post-processor interface.
 */
@DoNotImplement
interface GenericQueryPostProcessor<I, R> {
    /**
     * Name of this post-processor implementation, used to identify post-processor instances.
     */
    val name: String

    /**
     * If false, implementation will not be usable from RPC APIs.
     */
    val availableForRPC: Boolean get() = false

    /**
     * Lazily post-process a [Stream] of inputs of type [I] and return [Stream] of type [R].
     */
    fun postProcess(inputs: Stream<I>): Stream<R>
}