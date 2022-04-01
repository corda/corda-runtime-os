package net.corda.membership.httprpc.v1.types.response

data class RpcMemberInfoList(
    val members: List<RpcMemberInfo>
)

data class RpcMemberInfo(
    val memberContext: Map<String, String?>,
    val mgmContext: Map<String, String?>
)