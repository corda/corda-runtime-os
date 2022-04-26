package net.corda.sandboxgroupcontext.service

// NOTE: this interface is a bit a hack and only here so we can configure the cache from the sandbox
//  OSGi integration tests.
//  Once we have a good way of faking/stubbing the lifecycle coordinator, then I think we can remove this.
interface CacheConfiguration {
    fun initCache(cacheSize: Long)
}