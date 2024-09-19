package net.corda.common.json.validation.impl


import net.corda.libs.json.validator.JsonValidator
import net.corda.libs.json.validator.impl.JsonValidatorImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ JsonValidator::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    scope = ServiceScope.PROTOTYPE
)
class JsonValidatorOsgiImpl: JsonValidatorImpl(), UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken
