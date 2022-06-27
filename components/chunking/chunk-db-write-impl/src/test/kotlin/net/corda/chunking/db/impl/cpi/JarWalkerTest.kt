package net.corda.chunking.db.impl.cpi

import net.corda.chunking.db.impl.cpi.liquibase.JarWalker
import net.corda.test.util.InMemoryZipFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JarWalkerTest {
    companion object {
        private const val text = "some words"
    }

    private fun nonEmptyJar() = InMemoryZipFile().also {
        it.addEntry("resources/words1.txt", text.toByteArray())
        it.addEntry("resources/words2.txt", text.toByteArray())
    }

    private fun emptyJar() = InMemoryZipFile()

    @Test
    fun `empty jar has zero entries`() {
        var entryCount = 0
        emptyJar().inputStream().use { JarWalker.walk(it) { _, _ -> entryCount++ } }
        assertThat(entryCount).isEqualTo(0)
    }

    @Test
    fun `non empty jar has two entries`() {
        var entryCount = 0
        nonEmptyJar().inputStream().use { JarWalker.walk(it) { _, _ -> entryCount++ } }
        assertThat(entryCount).isEqualTo(2)
    }

    @Test
    fun `non empty jar returns content correctly`() {
        var entryCount = 0

        // I'm assuming the entries are in order
        nonEmptyJar().inputStream().use {
            JarWalker.walk(it) { path, inputStream ->
                entryCount++
                assertThat(inputStream.readAllBytes()).isEqualTo(text.toByteArray())
                assertThat(path).isEqualTo("resources/words$entryCount.txt")
            }
        }
        assertThat(entryCount).isEqualTo(2)
    }
}
