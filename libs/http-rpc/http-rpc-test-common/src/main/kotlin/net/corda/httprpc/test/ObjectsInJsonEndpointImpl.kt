package net.corda.httprpc.test

import net.corda.httprpc.JsonObject
import net.corda.httprpc.PluggableRPCOps
import net.corda.v5.base.util.contextLogger

class ObjectsInJsonEndpointImpl : ObjectsInJsonEndpoint, PluggableRPCOps<ObjectsInJsonEndpoint> {

    companion object {
        val log = contextLogger()
    }

    override val protocolVersion: Int = 1
    override val targetInterface = ObjectsInJsonEndpoint::class.java

    override fun createWithOneObject(creationObject: ObjectsInJsonEndpoint.RequestWithJsonObject):
            ObjectsInJsonEndpoint.ResponseWithJsonObject {
        log.info("Create with one object: $creationObject")
        return ObjectsInJsonEndpoint.ResponseWithJsonObject(creationObject.id, creationObject.obj)
    }

    override fun createWithIndividualParams(id: String, obj: JsonObject): ObjectsInJsonEndpoint.ResponseWithJsonObject {
        log.info("Create with individual params: id: $id object: $obj")
        return ObjectsInJsonEndpoint.ResponseWithJsonObject(id, obj)
    }
}