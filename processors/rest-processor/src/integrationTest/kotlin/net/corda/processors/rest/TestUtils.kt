package net.corda.processors.rpc

import java.nio.file.Path

val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")