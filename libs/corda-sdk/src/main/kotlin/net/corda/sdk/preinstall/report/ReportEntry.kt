package net.corda.sdk.preinstall.report

class ReportEntry(private var check: String, var result: Boolean, var reason: Exception? = null) {
    override fun toString(): String {
        var entry = "$check: ${if (result) "PASSED" else "FAILED"}"
        if (!result) {
            reason?.message?.let { entry += "\n\t - $it" }
            reason?.cause?.let { entry += "\n\t - $it" }
        }
        return entry
    }
}
