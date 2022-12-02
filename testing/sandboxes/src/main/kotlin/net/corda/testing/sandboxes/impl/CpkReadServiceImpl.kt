package net.corda.testing.sandboxes.impl

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.testutils.cpb.packaging.v2.TestCpbReaderV2
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
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
    name = CpiLoader.COMPONENT_NAME,
    service = [ CpkReadService::class, CpiLoader::class ],
    configurationPolicy = REQUIRE
)
@ServiceRanking(SandboxSetup.SANDBOX_SERVICE_RANKING)
class CpkReadServiceImpl @Activate constructor(
    bundleContext: BundleContext,
    properties: Map<String, Any?>
) : CpkReadService, CpiLoader {
    private val logger = loggerFor<CpkReadService>()

    private val cpkDir = (properties[CpiLoader.BASE_DIRECTORY_KEY] as? String)?.let { Paths.get(it) }
        ?: throw IllegalStateException("Base directory not configured")

    private val testBundle = (properties[CpiLoader.TEST_BUNDLE_KEY] as? String)?.let(bundleContext::getBundle)
        ?: throw IllegalStateException("Test bundle not found")

    private val cpis = ConcurrentHashMap<CpiIdentifier, Cpi>()
    private val cpks: Collection<Cpk>
        get() = cpis.values.flatMap(Cpi::cpks)

    private val cpksMeta: Collection<CpkMetadata>
        get() = cpks.map(Cpk::metadata)

    override val isRunning: Boolean get() = true

    init {
        logger.info("Activated")
    }

    private fun getInputStream(resourceName: String): InputStream {
        return testBundle.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    override fun loadCPI(resourceName: String): Cpi {
        return getInputStream(resourceName).buffered().use { input ->
            TestCpbReaderV2.readCpi(input, expansionLocation = cpkDir)
        }.let { newCpi ->
            val cpiId = newCpi.metadata.cpiId
            cpis.putIfAbsent(cpiId, newCpi) ?: newCpi
        }
    }

    override fun unloadCPI(cpi: Cpi) {
        removeCpiMetadata(cpi.metadata.cpiId)
    }

    override fun removeCpiMetadata(id: CpiIdentifier) {
        logger.info("Removing CPI {}", id)
        cpis.remove(id)
    }

    override fun getAllCpiMetadata(): CompletableFuture<List<CpiMetadata>> {
        val cpiList = cpis.values.map(Cpi::metadata)
        return CompletableFuture.completedFuture(cpiList)
    }

    override fun getCpiMetadata(id: CpiIdentifier): CompletableFuture<CpiMetadata?> {
        val legacyCpi: Cpi? = cpis[id]
        val cpi: CpiMetadata? = legacyCpi?.metadata
        return CompletableFuture.completedFuture(cpi)
    }

    override fun get(cpkFileChecksum: SecureHash): Cpk? {
        return cpks.firstOrNull { it.metadata.fileChecksum == cpkFileChecksum }
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
