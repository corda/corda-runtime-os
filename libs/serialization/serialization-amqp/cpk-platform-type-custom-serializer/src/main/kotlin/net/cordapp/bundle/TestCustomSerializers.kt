package net.cordapp.bundle

import net.corda.v5.base.versioning.Version
import net.corda.v5.serialization.SerializationCustomSerializer

// Custom serializer of platform type should be blocked.
class VersionSerializer : SerializationCustomSerializer<Version, VersionProxy> {
    override fun toProxy(obj: Version) =
        VersionProxy(obj.major, obj.minor)

    override fun fromProxy(proxy: VersionProxy) =
        Version(proxy.major, proxy.minor)
}
class VersionProxy(val major: Int, val minor: Int)

// Custom Serializer of JDK type should be blocked.
class ThreadSerializer : SerializationCustomSerializer<Thread, TestThreadProxy> {
    override fun toProxy(obj: Thread): TestThreadProxy =
        TestThreadProxy(obj.name)

    override fun fromProxy(proxy: TestThreadProxy): Thread =
        Thread(proxy.name)

}
class TestThreadProxy(val name: String)