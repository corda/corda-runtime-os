package net.cordapp.testing.utxo

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

@CordaSerializable
data class Block(val inputs: List<Long>, val owner: String, val amount: Long, val timestamp: Long = timestamp())

// @@@ Hash collisions are prevented only because of this, it's a bit crude
private fun timestamp() = Instant.now().toEpochMilli()
