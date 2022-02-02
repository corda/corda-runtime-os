package net.corda.chunking

import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

fun SecureHash.toAvro(): net.corda.data.crypto.SecureHash =
    net.corda.data.crypto.SecureHash(this.algorithm, ByteBuffer.wrap(bytes))

fun net.corda.data.crypto.SecureHash.toCorda(): SecureHash =
    SecureHash(this.algorithm, this.serverHash.array())
