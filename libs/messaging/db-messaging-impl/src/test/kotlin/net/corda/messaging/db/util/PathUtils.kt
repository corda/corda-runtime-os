package net.corda.messaging.db.util

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Deletes this path (if it exists) and if it's a directory, all its child paths recursively. */
fun Path.deleteRecursively() {
    if (!Files.exists(this)) return
    Files.walkFileTree(
        this,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exception: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        }
    )
}
