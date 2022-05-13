package net.corda.cpk.read

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.libs.packaging.Cpk

interface CpkReadService : Lifecycle {
    fun get(cpkId: CpkIdentifier): Cpk?
}
