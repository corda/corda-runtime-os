package net.corda.flow.sandbox

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.flow.sandbox.SandboxSetup.Companion.BASE_DIRECTORY_KEY
import net.corda.install.InstallService
import net.corda.install.InstallServiceListener
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE
import org.osgi.service.component.propertytypes.ServiceRanking

interface LoaderService {
    fun loadCPI(resourceName: String): CPI
    fun unloadCPI(cpi: CPI)

    fun get(id: CPI.Identifier): CompletableFuture<CPI?>
    fun remove(id: CPI.Identifier)
}

@Suppress("unused")
@Component(
    service = [ InstallService::class, LoaderService::class ],
    configurationPolicy = REQUIRE,
    configurationPid = [
        "org.osgi.service.cm.ConfigurationAdmin"
    ]
)
@ServiceRanking(Int.MAX_VALUE)
class InstallServiceImpl @Activate constructor(
    properties: Map<String, Any?>
) : InstallService, LoaderService {
    private val logger = loggerFor<InstallService>()

    private val cpkDir: Path =
        (properties[BASE_DIRECTORY_KEY] as? String)?.let { Paths.get(it) }
            ?: throw IllegalStateException("Base directory not configured")

    private val cpis = ConcurrentHashMap<CPI.Identifier, CPI>()
    private val cpks: Collection<CPK>
        get() = cpis.values.flatMap(CPI::cpks)

    override val isRunning: Boolean get() = true

    private fun getInputStream(resourceName: String): InputStream {
        return this::class.java.classLoader.getResource(resourceName)?.openStream()
            ?: throw FileNotFoundException("No such resource: '$resourceName'")
    }

    override fun loadCPI(resourceName: String): CPI {
        return getInputStream(resourceName).buffered().use { input ->
            CPI.from(input, expansionLocation = cpkDir, verifySignature = true)
        }.also { cpi ->
            val cpiId = cpi.metadata.id
            if (cpis.putIfAbsent(cpiId, cpi) != null) {
                throw FileAlreadyExistsException("CPI $cpiId already installed")
            }
        }
    }

    override fun unloadCPI(cpi: CPI) {
        remove(cpi.metadata.id)
    }

    override fun remove(id: CPI.Identifier) {
        cpis.remove(id)
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

    override fun stop() {
        logger.info("Stopped")
    }
}
