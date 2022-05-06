package net.corda.virtualnode.read.fake

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.io.File
import java.io.Reader


@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, VirtualNodeInfoReadServiceFake::class])
class VirtualNodeInfoReadServiceFake internal constructor(
    map: Map<HoldingIdentity, VirtualNodeInfo>,
    callbacks: List<VirtualNodeInfoListener>,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : VirtualNodeInfoReadService {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
    ) : this(emptyMap(), emptyList(), coordinatorFactory)

    companion object {
        val logger = contextLogger()
    }

    private val map: MutableMap<HoldingIdentity, VirtualNodeInfo> = map.toMutableMap()
    private val callbacks: MutableList<VirtualNodeInfoListener> = callbacks.toMutableList()

    init {
        val file = File("virtual-node-infos.yml")
        if (file.exists()) file.reader().use(::loadFrom)
    }

    private fun updateCoordinatorStatus(status: LifecycleStatus) {
        logger.info("Update coordinator status $status")
        if (status in listOf(LifecycleStatus.UP, LifecycleStatus.DOWN)) coordinator.updateStatus(status)
    }

    private val coordinator = coordinatorFactory.createCoordinator<VirtualNodeInfoReadService> { ev, _ ->
        when (ev) {
            is StartEvent -> logger.info("StartEvent received.")
            is StopEvent -> logger.info("StopEvent received.")
            is ErrorEvent -> logger.info("ErrorEvent ${ev.cause.message}")
            is RegistrationStatusChangeEvent -> updateCoordinatorStatus(ev.status)
            else -> logger.info("Other event received $ev")
        }
    }

    fun addOrUpdate(virtualNodeInfo: VirtualNodeInfo) {
        map[virtualNodeInfo.holdingIdentity] = virtualNodeInfo
        callbacks.forEach { it.onUpdate(setOf(virtualNodeInfo.holdingIdentity), map) }
    }

    fun remove(virtualNodeInfo: VirtualNodeInfo) {
        checkNotNull(map.remove(virtualNodeInfo.holdingIdentity)) {
            "VirtualNodeInfo ${virtualNodeInfo.holdingIdentity} doesn't exist"
        }
        callbacks.forEach { it.onUpdate(setOf(virtualNodeInfo.holdingIdentity), map) }
    }

    fun reset() {
        map.clear()
        callbacks.clear()
    }

    fun waitUntilRunning() {
        repeat(10) {
            if (isRunning) return
            Thread.sleep(100)
        }
        check(false) { "Timeout waiting for ${this::class.simpleName} to start" }
    }

    fun loadFrom(reader: Reader) {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule.Builder().withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false).configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false).configure(KotlinFeature.StrictNullChecks, false)
            .build())

        val listWrapperWorkaround = object : Any() {
            var virtualNodeInfos: List<VirtualNodeInfo> = emptyList()
        }

        val value = mapper.readValue(reader, listWrapperWorkaround::class.java)
        map += value.virtualNodeInfos.associateBy { it.holdingIdentity }
    }

    override fun getAll(): List<VirtualNodeInfo> {
        throwIfNotRunning()
        return map.values.toList()
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        throwIfNotRunning()
        return map[holdingIdentity]
    }

    override fun getById(id: String): VirtualNodeInfo? {
        throwIfNotRunning()
        return map.entries.firstOrNull { id == it.key.id }?.value
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        throwIfNotRunning()
        callbacks.add(listener)
        listener.onUpdate(map.keys, map)
        return AutoCloseable { callbacks.remove(listener) }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun close() {
        coordinator.close()
    }

    private fun throwIfNotRunning() {
        val reallyRunning = isRunning
        check(reallyRunning) { "${this::class.simpleName} has not been started." }
    }
}