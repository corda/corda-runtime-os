package net.corda.processors.db.internal.db

// TODO - Joel - Describe.
interface DBWriter {
    // TODO - Joel - Describe.
    fun writeEntity(entities: List<Any>)
}