package net.corda.chunking.read

import net.corda.lifecycle.Lifecycle

/**
 * Service that reads chunks from Kafka and puts them into the database.
 */
interface ChunkReadService : Lifecycle
