package net.corda.bundle.evolution.newer

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

/**
 * Serializable class used to test evolution to newer CPKs.
 */
@CordaSerializable
// Uncomment this version of the class when rebuilding test resources
//data class SerializableStateToNewerVersion(val id: UUID)
data class SerializableStateToNewerVersion(val id: UUID, val addedField: String?)
