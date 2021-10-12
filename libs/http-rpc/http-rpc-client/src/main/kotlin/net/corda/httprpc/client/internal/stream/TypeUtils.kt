package net.corda.httprpc.client.internal.stream

import net.corda.httprpc.durablestream.DurableCursorTransferObject
import net.corda.httprpc.durablestream.DurableStreamContext
import org.apache.commons.lang3.reflect.TypeUtils
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * This helper class exists to operate on `:base-internal` classes which will not be visible in the caller module.
 */
object TypeUtils {
    fun parameterizePollResult(itemType: Type) : ParameterizedType {
        return TypeUtils.parameterize(DurableCursorTransferObject.Companion.PollResultImpl::class.java, itemType)
    }

    fun durableStreamContext(pos: Long, maxCount: Int): Any {
        return DurableStreamContext(pos, maxCount)
    }
}