package net.corda.v5.membership

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Information about a virtual node's endpoint (e.g. a virtual node's peer-to-peer gateway endpoint).
 *
 * Example usages:
 *
 * ```java
 * MemberInfo memberInfo = memberLookup.myInfo();
 * List<EndpointInfo> endpoints = memberInfo.getMemberProvidedContext().parseList("corda.endpoints", EndpointInfo.class);
 * String url = endpoints.get(0).getUrl();
 * int protocolVersion = endpoints.get(0).getProtocolVersion();
 * ```
 *
 * ``` kotlin
 * val memberInfo = memberLookup.myInfo()
 * val endpoints = memberInfo.memberProvidedContext.parseList("corda.endpoints", EndpointInfo::class.java)
 * val url = endpoints.first().url
 * val protocolVersion = endpoints.first().protocolVersion
 * ```
 *
 * @property url Full virtual node endpoint URL.
 * @property protocolVersion Version of end-to-end authentication protocol. If multiple versions are supported, multiple instances of
 * [EndpointInfo] can be created, each using a different protocol version.
 */
@CordaSerializable
interface EndpointInfo {
    val url: String
    val protocolVersion: Int
}