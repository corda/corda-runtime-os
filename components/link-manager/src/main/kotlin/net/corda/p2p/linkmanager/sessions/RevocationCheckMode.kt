package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.gateway.certificates.RevocationMode

enum class RevocationCheckMode {
    OFF, SOFT_FAIL, HARD_FAIL;
    fun toData() : RevocationMode? {
        return when (this) {
            OFF -> null
            SOFT_FAIL -> RevocationMode.SOFT_FAIL
            HARD_FAIL -> RevocationMode.SOFT_FAIL
        }
    }
}
