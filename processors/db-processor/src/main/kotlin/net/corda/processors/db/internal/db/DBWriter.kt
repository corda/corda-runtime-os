package net.corda.processors.db.internal.db

interface DBWriter {
    fun writeConfig(entity: Any)
}