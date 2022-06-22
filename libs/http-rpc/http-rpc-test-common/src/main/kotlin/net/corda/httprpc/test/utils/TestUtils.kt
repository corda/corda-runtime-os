package net.corda.httprpc.test.utils

import java.net.ServerSocket
import java.nio.file.Path

fun findFreePort() = ServerSocket(0).use { it.localPort }

val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")