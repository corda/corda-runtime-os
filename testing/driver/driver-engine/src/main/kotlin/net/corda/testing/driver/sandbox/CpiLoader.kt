package net.corda.testing.driver.sandbox

import java.util.concurrent.CompletableFuture
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata

interface CpiLoader {
    companion object {
        const val COMPONENT_NAME = "net.corda.testing.driver.sandbox.CpiLoader"
        const val FRAMEWORK_DIRECTORY_KEY = "frameworkDirectory"
        const val CACHE_DIRECTORY_KEY = "cacheDirectory"
    }

    fun loadCPI(resourceName: String): Cpi
    fun unloadCPI(cpi: Cpi)

    fun getAllCpiMetadata(): CompletableFuture<List<CpiMetadata>>
    fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?>
    fun removeCpiMetadata(id: CpiIdentifier)
}
