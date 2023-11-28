package net.corda.tracing

interface BatchPublishTracing {
    fun begin(recordHeaders: List<List<Pair<String, String>>>)

    fun complete()

    fun abort()
}
