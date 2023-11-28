package net.corda.utilities

import net.corda.base.internal.ByteSequence
import net.corda.base.internal.OpaqueBytesSubSequence

fun ByteArray.toByteSequence(): ByteSequence {
    return OpaqueBytesSubSequence(this, 0, this.size)
}
