package net.corda.cpk.read

import net.corda.libs.packaging.CpkIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.packaging.CPK

interface CpkReadService : Lifecycle {
    fun get(cpkId: CpkIdentifier): CPK?
}
