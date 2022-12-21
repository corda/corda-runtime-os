package net.corda.uniqueness.datamodel.serialize

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorInputStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateConflictImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorReferenceStateUnknownImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorTimeWindowOutOfBoundsImpl
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateDetailsImpl
import net.corda.v5.ledger.utxo.StateRef

/**
 * These mixins are needed because we cannot add these annotations to the API project and Jackson won't know
 * how to serialize certain interfaces like [UniquenessCheckError] because these have multiple implementations.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = UniquenessCheckErrorInputStateConflictImpl::class, name = "inputStateConflictImpl"),
    JsonSubTypes.Type(value = UniquenessCheckErrorInputStateUnknownImpl::class, name = "inputStateUnknownImpl"),
    JsonSubTypes.Type(value = UniquenessCheckErrorReferenceStateConflictImpl::class, name = "refStateConflictImpl"),
    JsonSubTypes.Type(value = UniquenessCheckErrorReferenceStateUnknownImpl::class, name = "refStateUnknownImpl"),
    JsonSubTypes.Type(value = UniquenessCheckErrorTimeWindowOutOfBoundsImpl::class, name = "timeWindowOobImpl"),
    JsonSubTypes.Type(value = UniquenessCheckErrorMalformedRequestImpl::class, name = "malformedReqImpl")
)
abstract class UniquenessCheckErrorTypeMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = UniquenessCheckStateDetailsImpl::class, name = "uniquenessStateDetailsImpl"),
)
abstract class UniquenessCheckStateDetailsTypeMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = StateRef::class, name = "stateRefImpl"),
)
abstract class UniquenessCheckStateRefTypeMixin
