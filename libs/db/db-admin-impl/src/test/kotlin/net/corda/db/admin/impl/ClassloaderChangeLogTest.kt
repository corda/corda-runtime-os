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

        cl.fetch("classloader://foo/migration/test/fred.txt", null)
        cl.fetch("migration/bar.txt", null)
        cl.fetch("test/foo.txt", null)

        assertThat(cl.changeLogFileList).containsExactlyInAnyOrder(
            "migration/bar.txt",
            "test/foo.txt",
            "classloader://foo/migration/test/fred.txt"
        )
    }


    @Test
    fun `when changeLogList return all master and fetched without duplicates`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        cl.fetch("classloader://foo/migration/test/fred.txt", null)
        cl.fetch("migration/bar.txt", null)
        cl.fetch("test/foo.txt", null)
        cl.fetch("migration/bar.txt", null)
        cl.fetch("test/foo.txt", null)

        assertThat(cl.changeLogFileList).containsExactlyInAnyOrder(
            "migration/bar.txt",
            "test/foo.txt",
            "classloader://foo/migration/test/fred.txt"
        )
    }

    @Test
    fun `when changeLogList called early return only master`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        cl.fetch("classloader://foo/migration/test/fred.txt", null)

        assertThat(cl.changeLogFileList).containsExactlyInAnyOrder(
            "classloader://foo/migration/test/fred.txt"
        )
    }

    @Test
    fun `when fetch full name with classloader return resources as stream`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("classloader://foo/migration/test/fred.txt", null).bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }


    @Test
    fun `when fetch full name with classloader with single slash return resources as stream`() {
        // liquibase internally normalizes paths down to have single slashes replacing runs of
        // multiple slashes, e.g. when using an include with a relative reference.
        // So we have to support that format.
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("classloader:/foo/migration/test/fred.txt", null).bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }

    @Test
    fun `when fetch full name and classloader prefix but unknown name throws not illegal argument`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://bar/migration/test/fred.txt", null)
        }
    }

    @Test
    fun `when fetch full name and relative path throws not illegal argument`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://foo/migration/test/fred.txt", "bar")
        }
    }

    @Test
    fun `when fetch path without classloader prefix return resources as stream`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("migration/test/fred.txt", null).bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }

    @Test
    fun `when fetch path on class loader that does not exist throws not found exception`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThat(cl.fetch("fred.txt", "migration/test/bar.txt").bufferedReader().use { it.readText() })
            .isEqualTo("freddy")
    }

    @Test
    fun `when fetch path relative with a relativeTo prefix return resources as stream`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThrows<FileNotFoundException> {
            cl.fetch("classloader://foo/migration/test/bob.txt", null)
        }
    }
    @Test
    fun `when fetch path relative with a bad relativeTo prefix throws not found`() {
        val cl = ClassloaderChangeLog(changelogFiles)

        assertThrows<FileNotFoundException> {
            cl.fetch("fred.txt", "migration/does-not-exist")
        }
    }

    @Test
    fun `when fetch invalid throw not found`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<FileNotFoundException> {
            cl.fetch("does-not-exist/test/fred.txt", null)
        }
    }

    @Test
    fun `when fetch with classloader null resource throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://foo", null)
        }
    }

    @Test
    fun `when fetch with classloader empty resource throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://foo/", null)
        }
    }

    @Test
    fun `when fetch with invalid classloader throw`() {
        val cl = ClassloaderChangeLog(changelogFiles)
        assertThrows<IllegalArgumentException> {
            cl.fetch("classloader://invalid/migration/test/fred.txt", null)
        }
    }
}
