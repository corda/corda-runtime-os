package net.corda.libs.packaging.internal.v2

import java.util.jar.JarEntry
import java.util.jar.JarInputStream

class JarEntryAndBytes(val entry: JarEntry, val bytes: ByteArray)

fun readJar(jarInputStream: JarInputStream): List<JarEntryAndBytes> = generateSequence {
    jarInputStream.nextJarEntry?.let { jarEntry ->
        JarEntryAndBytes(jarEntry, jarInputStream.readAllBytes())
    }
}.filterNot { it.entry.isDirectory }
    .toList()
