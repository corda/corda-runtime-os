package net.corda.flow.persistence.query

import net.corda.v5.base.annotations.Suspendable
import java.io.Serializable
import java.nio.ByteBuffer

fun interface ResultSetExecutor<R> : Serializable {

    @Suspendable
    fun execute(serializedParameters: Map<String, ByteBuffer>, offset: Int): Pair<List<ByteBuffer>, Int>
}