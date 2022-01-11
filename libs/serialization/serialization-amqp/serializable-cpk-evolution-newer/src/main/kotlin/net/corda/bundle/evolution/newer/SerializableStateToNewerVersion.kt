package net.corda.bundle.evolution.newer

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

/**
 * Serializable class used to test evolution to newer CPKs.
 */
@CordaSerializable
//data class SerializableStateToNewerVersion(val Id: UUID)
data class SerializableStateToNewerVersion(val Id:UUID,val AddedField:String?)
