package net.corda.rest.test

import net.corda.rest.PluggableRestResource

class TestEndpointVersioningRestResourceImpl :
    TestEndpointVersioningRestResource,
    PluggableRestResource<TestEndpointVersioningRestResource> {
    override val protocolVersion: Int
        get() = 3

    override val targetInterface: Class<TestEndpointVersioningRestResource>
        get() = TestEndpointVersioningRestResource::class.java

    override fun getUsingPath(id: String): String {
        return "Retrieved using path id: $id"
    }

    @Deprecated("Deprecated in favour of `getUsingPath()`")
    override fun getUsingQuery(id: String): String {
        return "Retrieved using query: $id"
    }
}

class TestResourceVersioningRestResourceImpl :
    TestResourceVersioningRestResource,
    PluggableRestResource<TestResourceVersioningRestResource> {
    override val protocolVersion: Int
        get() = 3

    override val targetInterface: Class<TestResourceVersioningRestResource>
        get() = TestResourceVersioningRestResource::class.java

    @Deprecated("Deprecated in favour of `getUsingPath()`")
    override fun getUsingQuery(id: String): String {
        return "Retrieved using query: $id"
    }

    override fun getUsingPath(id: String): String {
        return "Retrieved using path id: $id"
    }
}

class TestResourceMaxVersioningRestResourceImpl :
    TestResourceMaxVersioningRestResource,
    PluggableRestResource<TestResourceMaxVersioningRestResource> {
    override val protocolVersion: Int
        get() = 3

    override val targetInterface: Class<TestResourceMaxVersioningRestResource>
        get() = TestResourceMaxVersioningRestResource::class.java

    @Deprecated("Deprecated in favour of `getUsingPath()`")
    override fun getUsingQuery(id: String): String {
        return "Retrieved using query: $id"
    }

    override fun getUsingPath(id: String): String {
        return "Retrieved using path id: $id"
    }
}
