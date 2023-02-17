package net.corda.v5.membership;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Information about a virtual node's endpoint (e.g. a virtual node's peer-to-peer gateway endpoint).</p>
 *
 * <p>Example usages:</p>
 *
 * <ul>
 * <li>Java:<pre>{@code
 * MemberInfo memberInfo = memberLookup.myInfo();
 * List<EndpointInfo> endpoints = memberInfo.getMemberProvidedContext().parseList("corda.endpoints", EndpointInfo.class);
 * String url = endpoints.get(0).getUrl();
 * int protocolVersion = endpoints.get(0).getProtocolVersion();
 * }</pre></li>
 * <li>Kotlin:<pre>{@code
 * val memberInfo = memberLookup.myInfo()
 * val endpoints = memberInfo.memberProvidedContext.parseList("corda.endpoints", EndpointInfo::class.java)
 * val url = endpoints.first().url
 * val protocolVersion = endpoints.first().protocolVersion
 * }</pre></li>
 * </ul>
 */
@CordaSerializable
public interface EndpointInfo {

    /**
     * @return Full virtual node endpoint URL.
     */
    @NotNull String getUrl();

    /**
     * @return Version of end-to-end authentication protocol. If multiple versions are supported, multiple instances of
     * {@link EndpointInfo} can be created, each using a different protocol version.
     */
    int getProtocolVersion();
}
