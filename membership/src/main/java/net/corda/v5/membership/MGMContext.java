package net.corda.v5.membership;

import net.corda.v5.base.types.LayeredPropertyMap;

/**
 * Part of [MemberInfo], information is provided and added by MGM as part of member acceptance and upon updates
 * (eg. membership group status updates).
 *
 * Contains information such as the membership status, modification time, etc.
 *
 * Example usages:
 *
 * ```java
 * Set<Map.Entry<String, String>> mgmContextEntries = mgmContext.getEntries();
 * String status = mgmContext.parse("corda.status", String.class);
 * Boolean isMgm = mgmContext.parseOrNull("corda.mgm", Boolean.class);
 * Set<String> additionalInformationSet = mgmContext.parseSet("additional.names", String.class);
 * List<Long> additionalInformationList = mgmContext.parseList("additional.numbers", Long.class);
 * ```
 *
 * ```kotlin
 * val mgmContextEntries = mgmContext.entries
 * val status = mgmContext.parse("corda.status", String::class.java)
 * val isMgm = mgmContext.parseOrNull("corda.mgm", Boolean::class.java)
 * val additionalInformationSet = mgmContext.parseSet("additional.names", String::class.java)
 * val additionalInformationList = mgmContext.parseList("additional.numbers", Long::class.java)
 * ```
 *
 * Properties are exposed either through properties on interfaces in the public APIs, or internally through extension
 * properties.
 *
 * @see [LayeredPropertyMap] For further information on the properties and functions.
 */
public interface MGMContext extends LayeredPropertyMap {}
