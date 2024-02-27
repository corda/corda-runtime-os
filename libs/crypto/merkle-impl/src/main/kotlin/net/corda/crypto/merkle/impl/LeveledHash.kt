package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash

data class LeveledHash(val level: Int, val index: Int, val hash: SecureHash)
