package net.corda.tracing.brave


import zipkin2.Span
import zipkin2.reporter.Reporter

class CombinedSpanReporter(private val reporters: List<Reporter<Span>>) : Reporter<Span> {
    override fun report(span: Span?) {
        reporters.forEach { it.report(span) }
    }
}