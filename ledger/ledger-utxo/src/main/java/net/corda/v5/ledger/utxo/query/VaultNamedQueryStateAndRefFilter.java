package net.corda.v5.ledger.utxo.query;

import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.StateAndRef;

/**
 * Representation of an in-memory filter function that will be applied to the result set that was returned by the named
 * query. The result set in this case contains {@link StateAndRef}s.
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MyVaultNamedQueryFilter : VaultNamedQueryStateAndRefFilter<ContractState> {
 *
 *     override fun filter(state: StateAndRef<ContractState>, parameters: Map<String, Any>): Boolean {
 *         return true
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MyVaultNamedQueryFilter implements VaultNamedQueryStateAndRefFilter<ContractState> {
 *
 *     @Override
 *     public Boolean filter(StateAndRef<ContractState> state, Map<String, Object> parameters) {
 *         return true;
 *     }
 * }
 * }</pre></li></ul>
 *
 * @param <T> Type of the {@link StateAndRef} results returned from the database.
 */
public interface VaultNamedQueryStateAndRefFilter<T extends ContractState>
        extends VaultNamedQueryFilter<StateAndRef<T>> {
}
