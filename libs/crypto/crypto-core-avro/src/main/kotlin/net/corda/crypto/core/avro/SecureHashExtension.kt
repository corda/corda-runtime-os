package net.corda.crypto.core.avro

import java.nio.ByteBuffer
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.v5.crypto.SecureHash
import net.corda.data.crypto.SecureHash as AvroSecureHash

fun AvroSecureHash.toCorda(): SecureHash =
    SecureHashImpl(this.algorithm, this.bytes.array())

fun SecureHash.toAvro(): AvroSecureHash =
    AvroSecureHash(this.algorithm, ByteBuffer.wrap(bytes))
