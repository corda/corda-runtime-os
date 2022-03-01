package net.corda.install.local.file.impl

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.install.InstallService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Collections.emptyNavigableMap
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collector
import kotlin.concurrent.read
import kotlin.concurrent.write

class UpdatedCPIList(val delta: NavigableSet<CPI.Identifier>) : LifecycleEvent

@Suppress("unused", "TooManyFunctions")
@Component(service = [InstallService::class], scope = ServiceScope.SINGLETON)
class LocalPackageCache @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : InstallService, Lifecycle {

    private companion object {
        private const val CFG_KEY = "corda.cpi"
        private const val CACHE_DIR_PATH = "custom.corda.cpi.cacheDir"
        private val logger = contextLogger()
        private val cpiMapCollector: Collector<CPI, TreeMap<CPI.Identifier, CPI>, NavigableMap<CPI.Identifier, CPI>> =
            Collector.of(
                ::TreeMap,
                { map, el -> map[el.metadata.id] = el },
                { set1, set2 -> set1.putAll(set2); set1 },
                Collections::unmodifiableNavigableMap
            )
        private val cpkMapCollector: Collector<CPK, TreeMap<CpkIdentifier, CPK>, NavigableMap<CpkIdentifier, CPK>> =
            Collector.of(
                ::TreeMap,
                { map, el -> map[CpkIdentifier(
                    el.metadata.id.name,
                    el.metadata.id.version,
                    el.metadata.id.signerSummaryHash
                )] = el },
                { set1, set2 -> set1.putAll(set2); set1 },
                Collections::unmodifiableNavigableMap
            )
    }

    private val cacheDir = Files.createTempDirectory("packageCache")

    private data class PackageCache(
        val cpiByIdMap: NavigableMap<CPI.Identifier, CPI> = emptyNavigableMap(),
        val cpkByIdMap: NavigableMap<CpkIdentifier, CPK> = emptyNavigableMap(),
        val cpkByHashMap: Map<SecureHash, CPK> = emptyMap(),
    )

    private val lifecycleCoordinator = lifecycleCoordinatorFactory.createCoordinator<InstallService>(::eventHandler)

    private val packageCacheLock = ReentrantReadWriteLock(true)

    private var packageCache = PackageCache()

    private val subscribersLock = ReentrantReadWriteLock(true)

    private fun scanCPKs(packageRepository: Path): NavigableMap<CpkIdentifier, CPK> {
        return packageRepository.takeIf(Files::exists)?.let { path ->
            Files.list(path).use { stream ->
                stream.filter {
                    it.fileName.toString().endsWith(CPK.fileExtension)
                }.map {
                    Files.newInputStream(it).use { str -> CPK.from(str, cacheDir, it.toString(), true) }
                }.collect(cpkMapCollector)
            }
        } ?: emptyNavigableMap()
    }

    private fun scanCPIs(packageRepository: Path): NavigableMap<CPI.Identifier, CPI> {
        return packageRepository.takeIf(Files::exists)?.let { path ->
            Files.list(path).use { stream ->
                stream.filter {
                    val fileName = it.fileName.toString()
                    CPI.fileExtensions.any(fileName::endsWith)
                }.map {
                    Files.newInputStream(it).use { str -> CPI.from(str, cacheDir, it.toString(), true) }
                }.collect(cpiMapCollector)
            }
        } ?: emptyNavigableMap()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "${javaClass.name} service starting..." }
                onStartEvent()
            }
            is StopEvent -> {
                logger.debug { "${javaClass.name} service stopping." }
            }
            is ErrorEvent -> {
                logger.error(event.cause.message, event.cause)
            }
        }
    }

    private fun getIdOrClosestMatch(id: CPI.Identifier) : CPI.Identifier {
        // One code path in the flows code sets version to "1" and clearly needs to be fixed
        logger.error("THIS METHOD IS CALLED FOR TESTING ONLY AND MUST BE REMOVED IN A FUTURE RELEASE")
        logger.error("Put a breakpoint here to see why it has been called - likely a badly specified version")

        if (id in packageCache.cpiByIdMap) return id

        return packageCache.cpiByIdMap.keys.first { it.name == id.name }
            ?: throw CordaRuntimeException("No CPI found in local package cache with id:  ${id}")
    }

    override fun get(id: CpkIdentifier): CompletableFuture<CPK?> = packageCacheLock.read {
        CompletableFuture.completedFuture(packageCache.cpkByIdMap[id])
    }

    private fun setup() {
        lifecycleCoordinator.start()
    }

    private fun onStartEvent() {
        configurationReadService.registerForUpdates { changedKeys, config ->
            if (CFG_KEY in changedKeys) {
                scanDirectoryAndBuildCache(config[CFG_KEY]!!)
            }
            // allow us to pass the directory in via the bootstrap config.
            if (ConfigKeys.BOOT_CONFIG in changedKeys) {
                val cfg = config[ConfigKeys.BOOT_CONFIG]!!.toSafeConfig()

                if (cfg.hasPath(CACHE_DIR_PATH)) {
                    scanDirectoryAndBuildCache(cfg.getConfig(CACHE_DIR_PATH)!!)
                }
            }
        }
    }

    private fun scanDirectoryAndBuildCache(config: Config) {
        packageCacheLock.write {
            val repositoryFolder = (
                    config.getString("cacheDir")
                        ?: throw IllegalStateException("Missing configuration key"))
                .let(Path::of)
            val cpiByIdMap = scanCPIs(repositoryFolder)
            val cpkByIdMap = cpiByIdMap.values.stream().flatMap {
                it.cpks.stream()
            }.collect(cpkMapCollector)
            val cpkByHashMap = cpkByIdMap.values.associateBy { it.metadata.hash }
            val oldCpiSet = packageCache.cpiByIdMap.keys
            packageCache = PackageCache(cpiByIdMap, cpkByIdMap, cpkByHashMap)
            if (lifecycleCoordinator.status != LifecycleStatus.UP) {
                lifecycleCoordinator.updateStatus(LifecycleStatus.UP, "CPI cache is ready")
            }
            val newCpiSet = packageCache.cpiByIdMap.keys
            val delta = TreeSet<CPI.Identifier>().let {
                it.addAll(oldCpiSet - newCpiSet)
                it.addAll(newCpiSet - oldCpiSet)
                Collections.unmodifiableNavigableSet(it)
            }
            lifecycleCoordinator.postEvent(UpdatedCPIList(delta))
        }
    }

    fun teardown() {
        lifecycleCoordinator.stop()
        for (cpi in packageCache.cpiByIdMap.values) {
            cpi.close()
        }
        for (cpk in packageCache.cpkByIdMap.values) {
            cpk.close()
        }
        for (cpk in packageCache.cpkByHashMap.values) {
            cpk.close()
        }
        Files.walk(cacheDir)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        configurationReadService.start()
        setup()
    }

    override fun stop() = teardown()
}
