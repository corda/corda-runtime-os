package net.corda.membership.rest.v1.types.response

data class RestMemberInfoList(
    val members: List<RestMemberInfo>
)

data class RestMemberInfo(
    val memberContext: Map<String, String?>,
    val mgmContext: Map<String, String?>
)