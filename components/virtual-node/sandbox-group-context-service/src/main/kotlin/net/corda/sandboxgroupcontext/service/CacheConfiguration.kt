package net.corda.sandboxgroupcontext.service

// NOTE: this interface is a bit a hack and is only here so that we can configure the cache from the sandbox
//  OSGi integration tests.
//  Once we have a good way of faking/stubbing the lifecycle coordinator, then I think we can remove this.
interface CacheConfiguration {
    fun initCache(capacity: Long)
    fun flushCache()
}
