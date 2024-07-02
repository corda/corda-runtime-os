package net.corda.sdk.preinstall.report

class Report(private var entries: MutableList<ReportEntry> = mutableListOf()) {
    private fun getEntries(): MutableList<ReportEntry> {
        return entries
    }

    fun addEntry(entry: ReportEntry) {
        entries.add(entry)
    }

    fun addEntries(newEntries: List<ReportEntry>) {
        entries.addAll(newEntries)
    }

    fun addEntries(reportEntries: Report) {
        entries.addAll(reportEntries.getEntries())
    }

    // Fails if any of the report entries have failed. If no entries are found, the report passes.
    fun testsPassed(): Boolean {
        return entries.all { it.result }
    }

    fun failingTests(): String {
        return entries.filter { !it.result }.joinToString(separator = "\n")
    }

    override fun toString(): String {
        return entries.joinToString(separator = "\n")
    }
}
