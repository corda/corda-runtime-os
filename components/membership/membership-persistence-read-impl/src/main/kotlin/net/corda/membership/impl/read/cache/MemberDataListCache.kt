package net.corda.membership.impl.read.cache

/**
 * An extension of [MemberDataCache] which is used when the data to be cached for a member is a list of data.
 */
interface MemberDataListCache<T> : MemberDataCache<List<T>>