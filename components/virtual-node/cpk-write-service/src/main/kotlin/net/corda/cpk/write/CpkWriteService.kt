package net.corda.cpk.write

import net.corda.lifecycle.Lifecycle

interface CpkWriteService : Lifecycle {
    fun putMissingCpk()
}
