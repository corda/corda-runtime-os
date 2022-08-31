package net.cordapp.testing.bundles.cats
import net.corda.v5.base.annotations.CordaSerializable
import java.util.UUID

@CordaSerializable
data class CatKey(var id: UUID = UUID.randomUUID(), var name: String = "") : java.io.Serializable
