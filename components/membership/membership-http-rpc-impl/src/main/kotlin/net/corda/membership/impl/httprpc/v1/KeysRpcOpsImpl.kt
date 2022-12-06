package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.InvalidParamsException
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.httprpc.v1.types.response.KeyPairIdentifier
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.time.format.DateTimeParseException

@Component(service = [PluggableRPCOps::class])
class KeysRpcOpsImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : KeysRpcOps, PluggableRPCOps<KeysRpcOps>, Lifecycle {
    private companion object {
        fun CryptoSigningKey.toMetaData() = KeyMetaData(
            keyId = this.id,
            alias = this.alias,
            hsmCategory = this.category.uppercase(),
            scheme = this.schemeCodeName,
            masterKeyAlias = this.masterKeyAlias,
            created = this.created
        )
    }

    override fun listSchemes(
        tenantId: String,
        hsmCategory: String,
    ): Collection<String> = cryptoOpsClient.getSupportedSchemes(
        tenantId = tenantId,
        category = hsmCategory.uppercase()
    )

    @Suppress("ComplexMethod")
    override fun listKeys(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: String,
        category: String?,
        schemeCodeName: String?,
        alias: String?,
        masterKeyAlias: String?,
        createdAfter: String?,
        createdBefore: String?,
        ids: List<String>?,
    ): Map<String, KeyMetaData> {
        if (ids?.isNotEmpty() == true) {
            return cryptoOpsClient.lookup(
                tenantId = tenantId,
                ids = ids
            ).associate { it.id to it.toMetaData() }
        }
        val realOrderBy = try {
            CryptoKeyOrderBy.valueOf(orderBy.uppercase())
        } catch (e: IllegalArgumentException) {
            throw ResourceNotFoundException("Invalid order by: $orderBy, must be one of: ${CryptoKeyOrderBy.values().joinToString()}")
        }
        val filterMap = emptyMap<String, String>().let {
            if (category != null) {
                it + mapOf(CATEGORY_FILTER to category.uppercase())
            } else {
                it
            }
        }.let {
            if (schemeCodeName != null) {
                it + mapOf(SCHEME_CODE_NAME_FILTER to schemeCodeName)
            } else {
                it
            }
        }.let {
            if (alias != null) {
                it + mapOf(ALIAS_FILTER to alias)
            } else {
                it
            }
        }.let {
            if (masterKeyAlias != null) {
                it + mapOf(MASTER_KEY_ALIAS_FILTER to masterKeyAlias)
            } else {
                it
            }
        }.let {
            if (createdBefore != null) {
                try {
                    Instant.parse(createdBefore)
                } catch (e: DateTimeParseException) {
                    throw ResourceNotFoundException("Invalid created before time ($createdBefore)")
                }
                it + mapOf(CREATED_BEFORE_FILTER to createdBefore.toString())
            } else {
                it
            }
        }.let {
            if (createdAfter != null) {
                try {
                    Instant.parse(createdAfter)
                } catch (e: DateTimeParseException) {
                    throw ResourceNotFoundException("Invalid created after time ($createdAfter)")
                }
                it + mapOf(CREATED_AFTER_FILTER to createdAfter.toString())
            } else {
                it
            }
        }
        return cryptoOpsClient.lookup(
            tenantId,
            skip,
            take,
            realOrderBy,
            filterMap,
        ).associate { it.id to it.toMetaData() }
    }

    override fun generateKeyPair(
        tenantId: String,
        alias: String,
        hsmCategory: String,
        scheme: String
    ): KeyPairIdentifier {
        if (alias.isBlank()) {
            throw InvalidInputDataException(
                details = mapOf("alias" to "Empty alias")
            )
        }
        return try {
            KeyPairIdentifier(
                cryptoOpsClient.generateKeyPair(
                    tenantId = tenantId,
                    category = hsmCategory.uppercase(),
                    alias = alias,
                    scheme = scheme,
                ).publicKeyId()
            )
        } catch (e: KeyAlreadyExistsException) {
            throw ResourceAlreadyExistsException(e.message!!)
        } catch (e: InvalidParamsException) {
            throw InvalidInputDataException(e.message!!)
        }
    }

    override fun generateKeyPem(
        tenantId: String,
        keyId: String,
    ): String {
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")

        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())
        return keyEncodingService.encodeAsString(publicKey)
    }

    override val targetInterface = KeysRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<KeysRpcOps>(
        protocolVersion.toString()
    )
    private fun updateStatus(status: LifecycleStatus, reason: String) {
        coordinator.updateStatus(status, reason)
    }

    private fun activate(reason: String) {
        updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
    }

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        )
    )
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
