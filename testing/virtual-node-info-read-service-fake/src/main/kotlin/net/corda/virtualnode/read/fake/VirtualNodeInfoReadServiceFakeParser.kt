package net.corda.virtualnode.read.fake

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.virtualnode.VirtualNodeInfo
import java.io.File
import java.io.Reader

internal object VirtualNodeInfoReadServiceFakeParser {

    /**
     * Loads the virtual nodes info from the [file] specified. If the file doesn't exist, it returns an empty list.
     */
    fun loadFrom(file: File): List<VirtualNodeInfo> {
        return if (file.exists()) loadFrom(file.reader()) else emptyList()
    }

    /**
     * Loads the virtual nodes info from the [reader]. Data must be in yaml format.
     */
    fun loadFrom(reader: Reader) : List<VirtualNodeInfo> {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule.Builder().withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false).configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false).configure(KotlinFeature.StrictNullChecks, false)
            .build())

        val listWrapperWorkaround = object : Any() {
            var virtualNodeInfos: List<VirtualNodeInfo> = emptyList()
        }

        return mapper.readValue(reader, listWrapperWorkaround::class.java).virtualNodeInfos
    }
}