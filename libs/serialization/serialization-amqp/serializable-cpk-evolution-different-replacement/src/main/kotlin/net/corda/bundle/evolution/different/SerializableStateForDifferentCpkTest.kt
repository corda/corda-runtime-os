package net.corda.bundle.evolution.different

import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

/**
 * Serializable class used to test that we cannot swap CPKs when deserializing
 */
@CordaSerializable
data class SerializableStateForDifferentCpkTest(val Id: UUID)
