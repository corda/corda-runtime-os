package net.corda.v5.ledger.utxo.query;

import net.corda.v5.ledger.utxo.query.json.ContractStateVaultJsonFactory;
import net.corda.v5.ledger.utxo.query.registration.VaultNamedQueryBuilderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * The main interface that needs to be implemented by the named ledger queries. Implementing this interface will define how
 * the named query will be built and stored.
 *
 * @see ContractStateVaultJsonFactory to define how a given state type will be represented as a JSON string.
 *
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MyVaultNamedQueryFactory : VaultNamedQueryFactory {
 *
 *     override fun create(vaultNamedQueryBuilderFactory: VaultNamedQueryBuilderFactory) {
 *         vaultNamedQueryBuilderFactory.create("FIND_WITH_CORDA_COLUMNS")
 *             .whereJson(
 *                 "WHERE custom ->> 'TestUtxoState.testField' = :testField " +
 *                         "AND custom ->> 'Corda.participants' IN :participants " +
 *                         "AND custom ? :contractStateType " +
 *                         "AND custom ->> 'Corda.createdTimestamp' > :created_timestamp"
 *             )
 *             .filter(MyVaultNamedQueryFilter())
 *             .map(MyVaultNamedQueryTransformer())
 *             .collect(MyVaultNamedQueryCollector())
 *             .register()
 *
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MyVaultNamedQueryFactory implements VaultNamedQueryFactory {
 *     @Override
 *     public void create(@NotNull VaultNamedQueryBuilderFactory vaultNamedQueryBuilderFactory) {
 *         vaultNamedQueryBuilderFactory.create("FIND_WITH_CORDA_COLUMNS")
 *                 .whereJson(
 *                         "WHERE custom ->> 'TestUtxoState.testField' = :testField " +
 *                                 "AND custom ->> 'Corda.participants' IN :participants " +
 *                                 "AND custom ? :contractStateType " +
 *                                 "AND custom ->> 'Corda.createdTimestamp' > :created_timestamp"
 *                 )
 *                 .filter(new MyVaultNamedQueryFilter())
 *                 .map(new MyVaultNamedQueryTransformer())
 *                 .collect(new MyVaultNamedQueryCollector())
 *                 .register();
 *     }
 * }
 * }</pre></li></ul>
 *
 * For more details on how to use filters, mappers and collectors, refer to the following documentation:
 * <ul>
 * <li> For filters, see {@link VaultNamedQueryFilter}. </li>
 * <li> For mappers, see {@link VaultNamedQueryTransformer}. </li>
 * <li> For collectors, see {@link VaultNamedQueryCollector}. </li>
 * </ul>
 */
public interface VaultNamedQueryFactory {
    void create(@NotNull VaultNamedQueryBuilderFactory vaultNamedQueryBuilderFactory);
}
