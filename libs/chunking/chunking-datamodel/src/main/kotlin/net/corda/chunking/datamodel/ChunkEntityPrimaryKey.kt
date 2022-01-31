package net.corda.chunking.datamodel

import java.io.Serializable

/**
 * We have a composite primary key in the Chunks table, so we need
 * to specify this so that we do not return the same result when
 * querying.  We need to supply defaults so Kotlin creates the default
 * constructor that JPA seems to require.
 */
data class ChunkEntityPrimaryKey(val requestId: String = "", var partNumber: Int = -1) : Serializable
