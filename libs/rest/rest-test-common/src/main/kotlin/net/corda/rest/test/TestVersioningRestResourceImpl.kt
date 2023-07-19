package net.corda.rest.test

import net.corda.rest.PluggableRestResource

class TestVersioningRestResourceImpl : TestVersioningRestResource, PluggableRestResource<TestVersioningRestResource> {

    override val protocolVersion: Int
        get() = 3

    override val targetInterface: Class<TestVersioningRestResource>
        get() = TestVersioningRestResource::class.java

    override fun create(creationParams: TestVersioningRestResource.CreationParams): String {
        return "Created using: $creationParams"
    }

    override fun getUsingPath(id: String): String {
        return "Retrieved using path id: $id"
    }

    @Deprecated("Deprecated in favour of `getUsingPath()`")
    override fun getUsingQuery(id: String): String {
        return "Retrieved using query: $id"
    }

    override fun update(updateParams: TestVersioningRestResource.UpdateParams): String {
        return "Updated using params: $updateParams"
    }

    override fun deleteUsingPath(id: String): String {
        return "Deleted using id: $id"
    }

    override fun deleteUsingQuery(query: String): String {
        return "Deleted using query: $query"
    }

    override fun putInputEcho(echoParams: TestVersioningRestResource.EchoParams): TestVersioningRestResource.EchoResponse {
        return TestVersioningRestResource.EchoResponse(echoParams.content)
    }
}