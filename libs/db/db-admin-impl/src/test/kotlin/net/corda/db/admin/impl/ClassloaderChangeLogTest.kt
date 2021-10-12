package net.corda.db.admin.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

class ClassloaderChangeLogTest {
    val classLoader = this::class.java.classLoader
    val changelogFiles = linkedSetOf(
        ClassloaderChangeLog.ChangeLogResourceFiles("foo", listOf("migration/test/fred"), classLoader = classLoader)
    )

    @Test
    fun `when master log files generate full path`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThat(cl.masterChangeLogFiles).containsExactly("classloader://foo/migration/test/fred")
    }

    @Test
    fun `when master log file with flash escape`() {
        val cl = ClassloaderChangeLog(
            linkedSetOf(ClassloaderChangeLog.ChangeLogResourceFiles("fo/o", listOf("migration/test/fred"), classLoader = classLoader))
        )
        assertThat(cl.masterChangeLogFiles).containsExactly("classloader://fo%2Fo/migration/test/fred")
    }

    @Test
    fun `when changeLogList return all master and fetched`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        cl.fetch("classloader://foo/migration/test/fred.txt")
        cl.fetch("migration/bar.txt")
        cl.fetch("test/foo.txt")

        assertThat(cl.changeLogFileList).containsExactlyInAnyOrder(
            "migration/bar.txt",
            "test/foo.txt",
            "classloader://foo/migration/test/fred.txt"
        )
    }

    @Test
    fun `when fetch full name return resources as stream`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("classloader://foo/migration/test/fred.txt").bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }

    @Test
    fun `when fetch relative path return resources as stream`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("migration/test/fred.txt").bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }

    @Test
    fun `when fetch invalid throw not found`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<FileNotFoundException> {
            cl.fetch("does-not-exist/test/fred.txt")
        }
    }

    @Test
    fun `when fetch with classloader null resource throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://foo")
        }
    }

    @Test
    fun `when fetch with classloader empty resource throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://foo/")
        }
    }

    @Test
    fun `when fetch with invalid classloader throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://invalid/migration/test/fred.txt")
        }
    }
}
