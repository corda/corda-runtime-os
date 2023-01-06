package net.cordapp.bundle

import net.corda.v5.base.versioning.Version
import net.corda.v5.serialization.SerializationCustomSerializer

// No need to implement methods in below testing serializers.
// We are not using them to serialize/ deserialize, only to assert they can be registered or not.

// Custom serializer for platform type should be denied.
class VersionSerializer : SerializationCustomSerializer<Version, VersionProxy> {
    override fun toProxy(obj: Version): VersionProxy {
        TODO("Not yet implemented")
    }

    override fun fromProxy(proxy: VersionProxy): Version {
        TODO("Not yet implemented")
    }
}
class VersionProxy(val major: Int, val minor: Int)

// Custom Serializer for JDK type should be denied.
class ThreadSerializer : SerializationCustomSerializer<Thread, TestThreadProxy> {
    override fun toProxy(obj: Thread): TestThreadProxy {
        TODO("Not yet implemented")
    }

    override fun fromProxy(proxy: TestThreadProxy): Thread {
        TODO("Not yet implemented")
    }
}
class TestThreadProxy(val name: String)


class SandboxType(val a: Int)
// Custom serializer for sandbox type should be allowed.
class SandboxTypeSerializer : SerializationCustomSerializer<SandboxType, Int> {
    override fun toProxy(obj: SandboxType): Int {
        TODO("Not yet implemented")
    }

    override fun fromProxy(proxy: Int): SandboxType {
        TODO("Not yet implemented")
    }
}
