package net.corda.v5.membership;

import net.corda.v5.base.types.LayeredPropertyMap;

/**
 * Part of [MemberInfo], MemberContext part is provided by the member as part of the initial MemberInfo proposal (i.e.
 * group registration).
 *
 * Contains information such as the node's endpoints, x500 name, key information, etc.
 *
 * Example usages:
 *
 * ```java
 * Set<Map.Entry<String, String>> memberContextEntries = memberContext.getEntries();
 * String groupId = memberContext.parse("corda.groupId", String.class);
 * Instant modifiedTime = memberContext.parseOrNull("corda.modifiedTime", Instant.class);
 * Set<String> additionalInformation = memberContext.parseSet("additional.names", String.class);
 * List<EndpointInfo> endpoints = memberContext.parseList("corda.endpoints", EndpointInfo.class);
 * ```
 *
 * ```kotlin
 * val entries = memberContext.entries
 * val groupId = memberContext.parse("corda.groupId", String::class.java)
 * val modifiedTime = memberContext.parseOrNull("corda.modifiedTime", Instant::class.java)
 * val additionalInformation = memberContext.parseSet("additional.names", String::class.java)
 * val endpoints = memberContext.parseList("corda.endpoints", EndpointInfo::class.java)
 * ```
 *
 * Properties are exposed either through properties on interfaces in the public APIs, or internally through extension
 * properties.
 *
 * @see [LayeredPropertyMap] For further information on the properties and functions.
 */
public interface MemberContext extends LayeredPropertyMap {}
