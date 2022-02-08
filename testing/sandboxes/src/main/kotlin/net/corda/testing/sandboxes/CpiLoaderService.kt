package net.corda.testing.sandboxes

import java.util.concurrent.CompletableFuture
import net.corda.packaging.CPI

interface CpiLoaderService {
    fun loadCPI(resourceName: String): CPI
    fun unloadCPI(cpi: CPI)

    fun get(id: CPI.Identifier): CompletableFuture<CPI?>
    fun remove(id: CPI.Identifier)

    fun stop()
}
