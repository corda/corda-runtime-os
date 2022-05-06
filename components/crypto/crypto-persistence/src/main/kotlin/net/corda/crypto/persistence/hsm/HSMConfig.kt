package net.corda.crypto.persistence.hsm

import net.corda.data.crypto.wire.hsm.HSMInfo

class HSMConfig(
    val info: HSMInfo,
    val serviceConfig: ByteArray
)