package net.corda.install.internal.utilities

import net.corda.utilities.copyTo
import net.corda.utilities.exists
import net.corda.utilities.isDirectory
import net.corda.utilities.list
import net.corda.utilities.toPath
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component
import java.io.File
import java.io.Reader
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Handles I/O for the various `InstallService` components. */
@Component(service = [FileUtils::class])
internal class FileUtils {
    companion object {
        private const val TEMP_DIR_PREFIX = "corda-install-"
    }

    /**
     * Returns the URIs of files in the [directories] with the specified [extension].
     *
     * Files in sub-folders are ignored. The [extension] should not contain a leading `.`. Throws
     * [IllegalArgumentException] if a path does not correspond to an actual directory.
     */
    fun getFilesWithExtension(directories: Collection<Path>, extension: String): Set<URI> {
        val invalidDirs = directories.filterNot(Path::isDirectory)
        if (invalidDirs.isNotEmpty()) {
            val nonExistentDirs = invalidDirs.filterNot(Path::exists)
            val nonDirectories = invalidDirs - nonExistentDirs
            throw IllegalArgumentException("The following directories are invalid: $invalidDirs. Of these, the " +
                    "following don't exist: $nonExistentDirs, and the following aren't directories: $nonDirectories.")
        }

        return directories
                .flatMap { directory ->
                    directory.list().filter { path -> path.toString().endsWith(".$extension") }
                }.mapTo(LinkedHashSet()) { path ->
                    path.toUri()
                }
    }

    /** As above, but for a single [directory]. */
    fun getFilesWithExtension(directory: Path, extension: String) = getFilesWithExtension(listOf(directory), extension)

    /** Unpacks the [zip] to an autogenerated temporary directory, and returns the directory's path. */
    fun unzip(zipUri: URI): Path {
        // Each zip receives its own temporary directory to avoid conflicts.
        val tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX)
        Runtime.getRuntime().addShutdownHook(Thread {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(Files::delete)
        })

        // We retrieve the relative paths of the zip entries.
        val zipEntryNames = ZipFile(zipUri.path).use { zipFile ->
            zipFile.entries().toList().map(ZipEntry::getName)
        }

        // We create a filesystem to copy the zip entries to a temporary directory.
        FileSystems.newFileSystem(zipUri.toPath(), null).use { fs ->
            zipEntryNames
                    .map(fs::getPath)
                    .filterNot(Path::isDirectory)
                    .forEach { path ->
                        val destination = tempDir.resolve(path.toString())
                        Files.createDirectories(destination.parent)
                        path.copyTo(destination)
                    }
        }

        return tempDir
    }

    /** Reads the [Manifest] of the JAR at [jarUri]. Throws [IllegalStateException] if the manifest cannot be read. */
    fun getManifest(jarUri: URI): Manifest = JarFile(jarUri.path).use(JarFile::getManifest)
            ?: throw IllegalStateException("The JAR at $jarUri does not contain a manifest.")

    /** Returns the hash of the JAR at [jarUri]. */
    fun getHash(jarUri: URI): SecureHash {
        val algorithm = DigestAlgorithmName.SHA2_256.name
        val messageDigest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        DigestInputStream(jarUri.toURL().openStream(), messageDigest).use { inputStream ->
            while (inputStream.read(buffer) != -1) {
                continue
            }
        }

        return SecureHash(algorithm, messageDigest.digest())
    }

    /** Returns the certificates for the JAR at [jarUri]. */
    fun getCertificates(jarUri: URI): Set<Certificate> = jarUri.toURL().openStream()
            .let(::JarInputStream)
            .use(JarSignatureCollector::collectCertificates)

    /** Returns the text of the entry [entryName] in the zip at [zipUri]. */
    fun readFileFromZip(zipUri: URI, entryName: String): String {
        return ZipFile(zipUri.path).use { zip ->
            val zipEntryStream = zip.getInputStream(ZipEntry(entryName))
            zipEntryStream.bufferedReader().use(Reader::readText)
        }
    }

    /** Copies [inputFile] to [outputFile]. */
    fun copyFile(inputFile: File, outputFile: File) = inputFile.copyTo(outputFile)

    /** Checks whether [file] exists. */
    fun exists(file: File) = file.exists()
}
