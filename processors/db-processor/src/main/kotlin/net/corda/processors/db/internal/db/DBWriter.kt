package net.corda.processors.db.internal.db

interface DBWriter {
    fun writeConfig(entities: List<Any>)
}