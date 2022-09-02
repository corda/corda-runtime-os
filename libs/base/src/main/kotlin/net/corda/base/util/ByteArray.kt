package net.corda.base.util

import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.types.OpaqueBytesSubSequence

fun ByteArray.toByteSequence() : ByteSequence {
    return OpaqueBytesSubSequence(this, 0, this.size)
}