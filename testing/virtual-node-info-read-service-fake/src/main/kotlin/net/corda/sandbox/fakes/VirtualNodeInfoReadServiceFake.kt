package net.corda.sandbox.fakes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.io.File
import java.io.Reader


@ServiceRanking(Int.MAX_VALUE)
@Component(service = [VirtualNodeInfoReadService::class, VirtualNodeInfoReadServiceFake::class])
class VirtualNodeInfoReadServiceFake internal constructor(
    map: Map<HoldingIdentity, VirtualNodeInfo>,
    callbacks: List<VirtualNodeInfoListener>,
) : VirtualNodeInfoReadService {

    @Activate
    constructor() : this(emptyMap(), emptyList())

    internal constructor(
        vararg virtualNodeInfos: VirtualNodeInfo,
        callbacks: List<VirtualNodeInfoListener> = emptyList(),
    ) : this(virtualNodeInfos.associateBy { it.holdingIdentity }, callbacks)

    private val map: MutableMap<HoldingIdentity, VirtualNodeInfo> = map.toMutableMap()
    private val callbacks: MutableList<VirtualNodeInfoListener> = callbacks.toMutableList()

    init {
        val file = File("virtual-node-infos.yml")
        if (file.exists()) file.reader().use(::loadFrom)
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
        value.virtualNodeInfos.forEach(::addOrUpdate)
    }

    override fun getAll(): List<VirtualNodeInfo> {
        return map.values.toList()
    }

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? {
        return map[holdingIdentity]
    }

    override fun getById(id: String): VirtualNodeInfo? {
        return map.entries.firstOrNull { id == it.key.id }?.value
    }

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable {
        callbacks.add(listener)
        listener.onUpdate(map.keys, map)
        return AutoCloseable { callbacks.remove(listener) }
    }

    override var isRunning: Boolean = false
        private set

    override fun start() {
        isRunning = true
        callbacks.forEach { it.onUpdate(map.keys, map) }
    }

    override fun stop() {
        isRunning = true
    }

    override fun close() {
        stop()
    }
}