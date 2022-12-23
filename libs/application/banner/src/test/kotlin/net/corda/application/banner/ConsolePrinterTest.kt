package net.corda.application.banner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConsolePrinterTest {
    private val printed = mutableListOf<String>()

    private fun mockPrinter(text: String) {
        printed.add(text)
    }

    @BeforeEach
    fun setup() {
        printed.clear()
    }

    @Test
    fun `when printPaddedLine with even width print`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printPaddedLine("test", 12)

        assertThat(printed).contains("--- test ---")
    }

    @Test
    fun `when printPaddedLine with odd width print`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printPaddedLine("test", 13)

        assertThat(printed).contains("--- test  ---")
    }

    @Test
    fun `when printPaddedLine with odd word width print`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printPaddedLine("testy", 12)

        assertThat(printed).contains("-- testy  --")
    }

    @Test
    fun `when printEmptyLine repeat spacer`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printEmptyLine(5)

        assertThat(printed).contains("-----")
    }

    @Test
    fun `when printLine just print`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printLine("hello world")

        assertThat(printed).contains("hello world")
    }

    @Test
    fun `when printLeftPad print`() {
        val printer = ConsolePrinter(this::mockPrinter)

        printer.printLeftPad("hello world", 4)

        assertThat(printed).contains("    hello world")
    }
}