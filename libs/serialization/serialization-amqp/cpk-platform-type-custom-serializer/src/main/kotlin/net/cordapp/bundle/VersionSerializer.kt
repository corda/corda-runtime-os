package net.cordapp.bundle

import net.corda.v5.base.versioning.Version
import net.corda.v5.serialization.SerializationCustomSerializer

// Serializer of platform type must be blocked.
class VersionSerializer : SerializationCustomSerializer<Version, VersionProxy> {
    override fun toProxy(obj: Version) =
        VersionProxy(obj.major, obj.minor)

    override fun fromProxy(proxy: VersionProxy) =
        Version(proxy.major, proxy.minor)
}

class VersionProxy(val major: Int, val minor: Int)