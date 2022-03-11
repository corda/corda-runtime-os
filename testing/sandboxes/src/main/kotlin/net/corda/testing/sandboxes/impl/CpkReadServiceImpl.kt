package net.corda.testing.sandboxes.impl

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.libs.packaging.CpkIdentifier
import net.corda.libs.packaging.CpkMetadata
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.testing.sandboxes.CpiLoader
import net.corda.v5.base.util.loggerFor
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.propertytypes.ServiceRanking
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
@Component(
    service = [ CpkReadService::class, CpiLoader::class ],
    configurationPolicy = REQUIRE
)
@ServiceRanking(Int.MAX_VALUE)
class CpkReadServiceImpl @Activate constructor(
    bundleContext: BundleContext,
    properties: Map<String, Any?>
) : CpkReadService, CpiLoader {
    companion object {
        const val BASE_DIRECTORY_KEY = "baseDirectory"
        const val TEST_BUNDLE_KEY = "testBundle"
    }

    private val logger = loggerFor<CpkReadService>()

    private val cpkDir = (properties[BASE_DIRECTORY_KEY] as? String)?.let { Paths.get(it) }
        ?: throw IllegalStateException("Base directory not configured")

    private val testBundle = (properties[TEST_BUNDLE_KEY] as? String)?.let(bundleContext::getBundle)
        ?: throw IllegalStateException("Test bundle not found")

    private val cpis = ConcurrentHashMap<CpiIdentifier, CPI>()
    private val cpks: Collection<CPK>
        get() = cpis.values.flatMap(CPI::cpks)

    private val cpksMeta: Collection<CpkMetadata>
        get() = cpks.map(CpkMetadata::fromLegacyCpk)

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
            val cpiId = CpiIdentifier.fromLegacy(newCpi.metadata.id)
            cpis.putIfAbsent(cpiId, newCpi)?.also {
                newCpi.close()
            } ?: newCpi
        }
    }

    override fun unloadCPI(cpi: CPI) {
        removeCpiMetadata(CpiIdentifier.fromLegacy(cpi.metadata.id))
    }

    override fun removeCpiMetadata(id: CpiIdentifier) {
        logger.info("Removing CPI {}", id)
        cpis.remove(id)?.close()
    }

    override fun getAllCpiMetadata(): CompletableFuture<List<CpiMetadata>> {
        val cpiList = cpis.values.map(CpiMetadata::fromLegacy)
        return CompletableFuture.completedFuture(cpiList)
    }

    override fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?> {
        val legacyCpi: CPI? = cpis[id]
        val cpi: CpiMetadata? = legacyCpi?.let(CpiMetadata::fromLegacy)
        return CompletableFuture.completedFuture(cpi)
    }

    override fun get(cpkId: CpkIdentifier): CPK? {
        val cpk = cpks.firstOrNull {
            it.metadata.id.name == cpkId.name &&
                    it.metadata.id.version == cpkId.version &&
                    it.metadata.id.signerSummaryHash == cpkId.signerSummaryHash }

        return cpk
    }

    override fun start() {
        logger.info("Started")
    }

    @Deactivate
    override fun stop() {
        ArrayList(cpis.keys).forEach(::removeCpiMetadata)
        logger.info("Stopped")
    }
}
