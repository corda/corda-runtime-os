package net.corda.crypto.persistence

import net.corda.data.crypto.wire.hsm.HSMInfo

class HSMStat(
    val usages: Int,
    val hsm: HSMInfo
)