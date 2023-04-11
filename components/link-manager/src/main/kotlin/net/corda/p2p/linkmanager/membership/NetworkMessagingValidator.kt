package net.corda.p2p.linkmanager.membership

import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NetworkMessagingValidator(private val membershipGroupReaderProvider: MembershipGroupReaderProvider) {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Validates whether [source] can participate in messaging with [destination] based on the network member list
     * visible to [destination].
     *
     * @returns NetworkStatusValidationResult indicating the result of the validation.
     */
    fun validateInbound(
        source: HoldingIdentity,
        destination: HoldingIdentity
    ) = validate(source, destination, ValidationDirection.Inbound)

    /**
     * Validates whether [source] can participate in messaging with [destination] based on the network member list
     * visible to [source].
     *
     * @returns NetworkStatusValidationResult indicating the result of the validation.
     */
    fun validateOutbound(
        source: HoldingIdentity,
        destination: HoldingIdentity
    ) = validate(source, destination, ValidationDirection.Outbound)

    private fun validate(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        validationDirection: ValidationDirection
    ): NetworkStatusValidationResult {
        val memberListViewOwner = when(validationDirection) {
            is ValidationDirection.Inbound -> destination
            is ValidationDirection.Outbound -> source
        }
        val sourceMemberInfo = getMemberInfo(memberListViewOwner, source.x500Name)
        val destinationMemberInfo = getMemberInfo(memberListViewOwner, destination.x500Name)

        return if (!canMessage(sourceMemberInfo, destinationMemberInfo)
            || !canMessage(destinationMemberInfo, sourceMemberInfo)
        ) {
            NetworkStatusValidationResult.Fail(
                "network membership status for the source identity (with name ${source.x500Name} in " +
                        "group ${source.x500Name}) is (${sourceMemberInfo?.status}) and the destination's (with name " +
                        "${destination.x500Name} in group ${destination.x500Name}) network membership status is " +
                        "(${destinationMemberInfo?.status})."
            )
        } else {
            NetworkStatusValidationResult.Pass
        }
    }

    /**
     * Validates the input by calling [validate] and invokes [func] if validation succeeds.
     * If validation fails, no exception is thrown and [func] is not invoked.
     * Validation is performed based on the network member list visible to [destination].
     *
     * @return result of [func] or null if validation failed.
     */
    fun <T> invokeIfValidInbound(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        func: () -> T
    ): T? = invokeIfValid(source, destination, ValidationDirection.Inbound, func)

    /**
     * Validates the input by calling [validate] and invokes [func] if validation succeeds.
     * If validation fails, no exception is thrown and [func] is not invoked.
     * Validation is performed based on the network member list visible to [source].
     *
     * @return result of [func] or null if validation failed.
     */
    fun <T> invokeIfValidOutbound(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        func: () -> T
    ): T? = invokeIfValid(source, destination, ValidationDirection.Outbound, func)

    private fun <T> invokeIfValid(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        validationDirection: ValidationDirection,
        func: () -> T
    ): T? = when (val result = validate(source, destination, validationDirection)) {
        is NetworkStatusValidationResult.Pass -> func()
        is NetworkStatusValidationResult.Fail -> {
            logger.warn(
                "Failed validation for allowed messaging from [${destination.x500Name}] to " +
                        "[${source.x500Name}] in group [${source.groupId}] because ${result.reason}"
            )
            null
        }
    }

    /**
     * Lookup active or suspended members. Members not yet in the member list or in pending state will both be returned
     * as null and only an MGM can send in this case so both pending and not yet added members are handled in the same
     * way.
     */
    private fun getMemberInfo(
        viewOwner: HoldingIdentity,
        x500Name: MemberX500Name
    ) = membershipGroupReaderProvider.getGroupReader(viewOwner).lookup(x500Name, ACTIVE_OR_SUSPENDED)

    /**
     * Return true if [party] can message [otherParty].
     *
     * If a [party] is not in the member list (i.e. null), or they are not active, they can only message [otherParty]
     * if [otherParty] is an MGM.
     * If [party] is an MGM, they can message [otherParty] in any status.
     * Otherwise, a party can message if [otherParty] is in active status.
     */
    private fun canMessage(party: MemberInfo?, otherParty: MemberInfo?) = when {
        party?.isMgm == true -> true
        party == null || !party.isActive -> otherParty?.isMgm == true
        else -> otherParty?.isActive == true
    }

    private sealed class ValidationDirection {
        object Inbound : ValidationDirection()
        object Outbound : ValidationDirection()
    }

}


sealed class NetworkStatusValidationResult {
    object Pass : NetworkStatusValidationResult()
    data class Fail(val reason: String) : NetworkStatusValidationResult()
}