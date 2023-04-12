package net.corda.rocks.db.api

enum class KeyValueOpType {
    Put,
    Delete
}

class WriteOp(
    val type: KeyValueOpType,
    val table: String,
    val key: ByteArray,
    val value: ByteArray?
)

class ReadOp(
    val table: String,
    val key: ByteArray
)

class ReadResult(
    val table: String,
    val key: ByteArray,
    val value: ByteArray?
)

fun interface QueryProcessor {
    fun process(key: ByteArray, value: ByteArray): Boolean
}