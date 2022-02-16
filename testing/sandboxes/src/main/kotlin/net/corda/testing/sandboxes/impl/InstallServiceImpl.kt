package net.corda.testing.sandboxes.impl

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.install.InstallService
import net.corda.install.InstallServiceListener
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.testing.sandboxes.CpiLoader
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("unused")
@Component(
    service = [ InstallService::class, CpiLoader::class ],
    configurationPolicy = REQUIRE
)
@ServiceRanking(Int.MAX_VALUE)
class InstallServiceImpl @Activate constructor(
    bundleContext: BundleContext,
    properties: Map<String, Any?>
) : InstallService, CpiLoader {
    companion object {
        const val BASE_DIRECTORY_KEY = "baseDirectory"
        const val TEST_BUNDLE_KEY = "testBundle"
    }

    private val logger = loggerFor<InstallService>()

    private val cpkDir = (properties[BASE_DIRECTORY_KEY] as? String)?.let { Paths.get(it) }
        ?: throw IllegalStateException("Base directory not configured")

    private val testBundle = (properties[TEST_BUNDLE_KEY] as? String)?.let(bundleContext::getBundle)
        ?: throw IllegalStateException("Test bundle not found")

    private val cpis = ConcurrentHashMap<CPI.Identifier, CPI>()
    private val cpks: Collection<CPK>
        get() = cpis.values.flatMap(CPI::cpks)

    override val isRunning: Boolean get() = true

    init {
        logger.info("Activated")
    }

    private fun getInputStream(resourceName: String): InputStream {
        return testBundle.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    override fun loadCPI(resourceName: String): CPI {
        return getInputStream(resourceName).buffered().use { input ->
            CPI.from(input, expansionLocation = cpkDir, verifySignature = true)
        }.let { newCpi ->
            val cpiId = newCpi.metadata.id
            cpis.putIfAbsent(cpiId, newCpi)?.also { newCpi.close() } ?: newCpi
        }
    }

    override fun unloadCPI(cpi: CPI) {
        remove(cpi.metadata.id)
    }

    override fun remove(id: CPI.Identifier) {
        logger.info("Removing CPI {}", id)
        cpis.remove(id)?.close()
    }

    override fun get(id: CPI.Identifier): CompletableFuture<CPI?> {
        return CompletableFuture.completedFuture(cpis[id])
    }

    override fun get(id: CPK.Identifier): CompletableFuture<CPK?> {
        return CompletableFuture.completedFuture(cpks.find { it.metadata.id == id })
    }

    override fun getCPKByHash(hash: SecureHash): CompletableFuture<CPK?> {
        return CompletableFuture.completedFuture(cpks.find { it.metadata.hash == hash })
    }

    override fun listCPK(): List<CPK.Metadata> {
        return cpks.map(CPK::metadata)
    }

    override fun listCPI(): List<CPI.Metadata> {
        return cpis.values.map(CPI::metadata)
    }

    override fun registerForUpdates(installServiceListener: InstallServiceListener): AutoCloseable {
        return AutoCloseable {}
    }

    override fun start() {
        logger.info("Started")
    }

    @Deactivate
    override fun stop() {
        ArrayList(cpis.values).forEach(::unloadCPI)
        logger.info("Stopped")
    }
}
