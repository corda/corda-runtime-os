package net.corda.bundle.evolution.older

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

/**
 * Serializable class used to test evolution to older CPKs.
 */
@CordaSerializable
// Uncomment this version of the class when rebuilding test resources
//data class SerializableStateToOlderVersion(val id: UUID, val removedField: String)
data class SerializableStateToOlderVersion(val id:UUID)
