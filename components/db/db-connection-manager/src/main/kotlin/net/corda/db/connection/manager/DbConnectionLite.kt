package net.corda.db.connection.manager

import java.util.UUID

data class DbConnectionLite(
    val id: UUID,
    val config: String,
)