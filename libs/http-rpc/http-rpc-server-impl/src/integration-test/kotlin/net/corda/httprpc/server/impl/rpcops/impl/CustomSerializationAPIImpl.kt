package net.corda.httprpc.server.impl.rpcops.impl


import net.corda.httprpc.server.impl.rpcops.CustomMarshalString
import net.corda.httprpc.server.impl.rpcops.CustomSerializationAPI
import net.corda.httprpc.server.impl.rpcops.CustomString
import net.corda.httprpc.server.impl.rpcops.CustomUnsafeString
import net.corda.v5.httprpc.api.PluggableRPCOps

class CustomSerializationAPIImpl : CustomSerializationAPI, PluggableRPCOps<CustomSerializationAPI> {
    override val targetInterface: Class<CustomSerializationAPI>
        get() = CustomSerializationAPI::class.java


    override val protocolVersion: Int
        get() = 2

    override fun printString(s: CustomString): CustomString {
        return s
    }

    override fun printUnsafeString(s: CustomUnsafeString): CustomUnsafeString {
        return s
    }

    override fun printCustomMarshalString(s: CustomMarshalString): CustomMarshalString {
        return s
    }
}