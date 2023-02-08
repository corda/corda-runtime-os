package net.corda.chunking

import net.corda.data.crypto.SecureHash as AvroSecureHash
import net.corda.v5.crypto.SecureHash

import java.nio.ByteBuffer

fun SecureHash.toAvro(): AvroSecureHash =
    AvroSecureHash(this.algorithm, ByteBuffer.wrap(bytes))

fun AvroSecureHash.toCorda(): SecureHash =
    SecureHash(this.algorithm, this.bytes.array())
