package net.corda.utilities

import java.io.InputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path

fun InputStream.copyTo(target: Path, vararg options: CopyOption): Long = Files.copy(this, target, *options)

/**
 * Simple Map structure that can be used as a cache in the DJVM.
 */
fun <K, V> createSimpleCache(maxSize: Int, onEject: (MutableMap.MutableEntry<K, V>) -> Unit = {}): MutableMap<K, V> {
    return object : LinkedHashMap<K, V>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            val eject = size > maxSize
            if (eject) onEject(eldest!!)
            return eject
        }
    }
}
