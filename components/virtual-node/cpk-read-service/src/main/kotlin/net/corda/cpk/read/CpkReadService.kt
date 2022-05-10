package net.corda.cpk.read

import net.corda.libs.packaging.CpkIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.packaging.Cpk

interface CpkReadService : Lifecycle {
    fun get(cpkId: CpkIdentifier): Cpk?
}
