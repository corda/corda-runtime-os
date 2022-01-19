package net.corda.cpk.readwrite

import net.corda.packaging.CPK
import java.nio.file.Path

/** Resolve the full path to the CPK in the given `baseDir` */
fun CPK.Metadata.resolvePath(baseDir: Path): Path = baseDir.resolve("${this.hash.toHexString()}.cpk")
