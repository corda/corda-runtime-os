package net.corda.testing.sandboxes

import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.packaging.Cpi
import java.util.concurrent.CompletableFuture

interface CpiLoader {
    fun loadCPI(resourceName: String): Cpi
    fun unloadCPI(cpi: Cpi)

    fun getAllCpiMetadata(): CompletableFuture<List<CpiMetadata>>
    fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?>
    fun removeCpiMetadata(id: CpiIdentifier)

    fun stop()
}
