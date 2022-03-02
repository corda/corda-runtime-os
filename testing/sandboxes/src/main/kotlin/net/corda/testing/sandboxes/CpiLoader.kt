package net.corda.testing.sandboxes

import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import java.util.concurrent.CompletableFuture
import net.corda.packaging.CPI

interface CpiLoader {
    fun loadCPI(resourceName: String): CPI
    fun unloadCPI(id: CpiIdentifier)

    fun get(id: CpiIdentifier): CompletableFuture<CpiMetadata?>
    fun remove(id: CpiIdentifier)

    fun stop()
}
