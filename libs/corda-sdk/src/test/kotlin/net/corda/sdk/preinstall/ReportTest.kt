package net.corda.sdk.preinstall

import net.corda.sdk.preinstall.report.Report
import net.corda.sdk.preinstall.report.ReportEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReportTest {

    @Test
    fun testPassingReport() {
        val report = Report()
        report.addEntries(mutableListOf(ReportEntry("Doesn't crash", true), ReportEntry("No bugs", true)))
        assertEquals(true, report.testsPassed())
    }

    @Test
    fun testFailingReport() {
        val report = Report()
        report.addEntry(ReportEntry("Is magical", false, Exception("Not magic")))
        assertEquals(false, report.testsPassed())
    }

    @Test
    fun testCombineReports() {
        val report = Report()
        report.addEntries(mutableListOf(ReportEntry("Doesn't crash", true), ReportEntry("No bugs", true)))
        val anotherReport = Report(mutableListOf(ReportEntry("Can combine with other reports", true)))
        report.addEntries(anotherReport)
        assertEquals(true, report.testsPassed())
    }
}
