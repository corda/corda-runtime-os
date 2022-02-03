package net.corda.utilities

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

@Tag("Unstable")
class PathUtilsTest {

    companion object {
        @JvmStatic
        private fun provideTempDir(): Stream<Arguments> {
            return Stream.of(
                    Arguments.of(Jimfs.newFileSystem(Configuration.unix()).getPath("/tmp").also { Files.createDirectory(it) }),
                    Arguments.of(Jimfs.newFileSystem(Configuration.windows()).getPath("C:\\tmp").also { Files.createDirectory(it) }),
                    Arguments.of(Jimfs.newFileSystem(Configuration.osX()).getPath("/tmp").also { Files.createDirectory(it) }))
        }
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - non-existent path`(tempDir: Path) {
        val path = tempDir / "non-existent"
        path.deleteRecursively()
        assertThat(path).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - file`(tempDir: Path) {
        val file = (tempDir / "file").createFile()
        file.deleteRecursively()
        assertThat(file).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - empty folder`(tempDir: Path) {
        val emptyDir = (tempDir / "empty").createDirectories()
        emptyDir.deleteRecursively()
        assertThat(emptyDir).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - dir with single file`(tempDir: Path) {
        val dir = (tempDir / "dir").createDirectories()
        (dir / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - nested single file`(tempDir: Path) {
        val dir = (tempDir / "dir").createDirectories()
        val dir2 = (dir / "dir2").createDirectories()
        (dir2 / "file").createFile()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `deleteRecursively - complex`(tempDir: Path) {
        val dir = (tempDir / "dir").createDirectories()
        (dir / "file1").createFile()
        val dir2 = (dir / "dir2").createDirectories()
        (dir2 / "file2").createFile()
        (dir2 / "file3").createFile()
        (dir2 / "dir3").createDirectories()
        dir.deleteRecursively()
        assertThat(dir).doesNotExist()
    }

    @ParameterizedTest
    @MethodSource("provideTempDir")
    fun `copyToDirectory - copy into zip directory`(tempDir: Path) {
        val source: Path = (tempDir / "source.txt").also { path ->
            Files.newBufferedWriter(path).use { writer ->
                writer.write("Example Text")
            }
        }
        val target = tempDir / "target.zip"
        FileSystems.newFileSystem(URI.create("jar:${target.toUri()}"), mapOf("create" to "true")).use { fs ->
            val dir = fs.getPath("dir").createDirectories()
            val result = source.copyToDirectory(dir)
            assertThat(result)
                    .isRegularFile()
                    .hasParent(dir)
                    .hasSameContentAs(source)
        }
    }
}
