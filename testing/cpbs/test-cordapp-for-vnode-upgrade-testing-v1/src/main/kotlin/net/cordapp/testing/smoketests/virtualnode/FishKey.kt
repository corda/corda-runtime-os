package net.cordapp.testing.smoketests.virtualnode
import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

@CordaSerializable
data class FishKey(var id: UUID = UUID.randomUUID(), var name: String = "") : java.io.Serializable
