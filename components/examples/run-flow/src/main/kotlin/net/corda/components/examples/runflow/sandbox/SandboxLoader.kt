package net.corda.components.examples.runflow.sandbox

import net.corda.sandbox.cache.SandboxCache
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Paths
import kotlin.streams.toList

@Component
class SandboxLoader @Activate constructor(
    @Reference(service = SandboxCache::class)
    private val sandboxCache: SandboxCache,
) {

    fun loadCPBs(CPBs: File?): SandboxCache {
        sandboxCache.loadCpbs(CPBs!!.readLines(Charset.defaultCharset()).stream().map { Paths.get(it) }.toList())
        return sandboxCache
    }

}