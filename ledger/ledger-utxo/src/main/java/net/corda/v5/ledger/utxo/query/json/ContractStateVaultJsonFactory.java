package net.corda.v5.ledger.utxo.query.json;

import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import org.jetbrains.annotations.NotNull;

/**
 * Implement a {@link ContractStateVaultJsonFactory} to create a JSON representation of a state to store alongside
 * the state in the vault. This JSON representation can be used to query states from flows using
 * {@link UtxoLedgerService#query}.
 * <p>
 * Classes that implement {@link ContractStateVaultJsonFactory} will be executed in a hierarchical way. That means
 * every factory that belongs to a given state or that given state's ancestors will be executed and the end result
 * will be a combined JSON. The combined JSON will be keyed by the state type specified by the factory.
 * <p>
 * In order to perform a "query by state" query using `UtxoLedgerService.query` for a particular state type,
 * a factory for that given type must be present, even if it's just returning an empty JSON string.
 * <p>
 * Please note that only one factory can be registered for a state type.
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class TestUtxoStateVaultJsonFactory: ContractStateVaultJsonFactory<TestUtxoState> {
 *
 *     private data class TestUtxoStatePojo(val testField: String)
 *
 *     override val stateType: Class<TestUtxoState> = TestUtxoState::class.java
 *
 *     override fun append(state: TestUtxoState, jsonMarshallingService: JsonMarshallingService): String {
 *         return jsonMarshallingService.format(TestUtxoStatePojo(state.testField))
 *     }
 * }
 *
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class TestUtxoStateVaultJsonFactory implements ContractStateVaultJsonFactory<TestUtxoState> {
 *
 *     private class TestUtxoStatePojo {
 *         private String testField;
 *
 *         TestUtxoStatePojo(String testField) {
 *             this.testField = testField;
 *         }
 *
 *         String getTestField() {
 *             return this.testField;
 *         }
 *     }
 *
 *     @Override
 *     public Class<T> getStateType() {
 *         return TestUtxoState.class;
 *     }
 *
 *     @Override
 *     public String append(TestUtxoState state, JsonMarshallingService jsonMarshallingService) {
 *         return jsonMarshallingService.format(new TestUtxoStatePojo(state.getTestField()));
 *     }
 * }
 * }</pre></li></ul>
 *
 * @param <T> The type of the state that this class belongs to. Must be a subtype of {@link ContractState}.
 */
public interface ContractStateVaultJsonFactory<T extends ContractState> {

    /**
     * @return The type of the state this factory belongs to.
     */
    @NotNull Class<T> getStateType();

    /**
     * The function that defines how the given state can be represented as a JSON string.
     *
     * @param state The state object.
     * @param jsonMarshallingService An instance of a {@link JsonMarshallingService} that can be used when creating a
     * JSON representation.
     *
     * @return The JSON representation as a String.
     */
    @NotNull String create(@NotNull T state, @NotNull JsonMarshallingService jsonMarshallingService);
}
