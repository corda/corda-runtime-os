package net.corda.processors.rest

import java.nio.file.Path

val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")
