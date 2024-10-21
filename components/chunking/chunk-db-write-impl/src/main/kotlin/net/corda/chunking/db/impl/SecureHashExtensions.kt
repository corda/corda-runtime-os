package net.corda.chunking.db.impl

import java.nio.ByteBuffer
import net.corda.crypto.core.bytes
import net.corda.data.crypto.SecureHash as AvroSecureHash
import net.corda.v5.crypto.SecureHash

fun SecureHash.toAvro(): AvroSecureHash =
    AvroSecureHash(this.algorithm, ByteBuffer.wrap(bytes))
