package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps

class TestEntityRpcOpsImpl : TestEntityRpcOps, PluggableRPCOps<TestEntityRpcOps> {

    override val protocolVersion: Int
        get() = 3

    override val targetInterface: Class<TestEntityRpcOps>
        get() = TestEntityRpcOps::class.java

    override fun create(creationParams: TestEntityRpcOps.CreationParams): String {
        return "Created using: $creationParams"
    }

    override fun getUsingPath(id: String): String {
        return "Retrieved using id: $id"
    }

    override fun getUsingQuery(query: String): String {
        return "Retrieved using query: $query"
    }

    override fun update(updateParams: TestEntityRpcOps.UpdateParams): String {
        return "Updated using params: $updateParams"
    }

    override fun deleteUsingPath(id: String): String {
        return "Deleted using id: $id"
    }

    override fun deleteUsingQuery(query: String): String {
        return "Deleted using query: $query"
    }
}