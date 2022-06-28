package net.corda.processors.rpc

import java.net.ServerSocket
import java.nio.file.Path

// We cannot use implementations from `http-rpc-test-common` module as it is not OSGi module
// And it cannot become OSGi module due to UniRest not being OSGi compliant
fun findFreePort() = ServerSocket(0).use { it.localPort }

val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")