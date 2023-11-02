package net.corda.flow

object FiberConstants {
    val maximumCacheSize: Int = Integer.getInteger("net.corda.flow.fiber.cache.maximumSize", 10000)
}
