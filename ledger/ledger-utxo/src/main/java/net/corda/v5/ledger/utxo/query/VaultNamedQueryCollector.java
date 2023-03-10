package net.corda.v5.ledger.utxo.query;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A collector that is applied to the result set returned after executing the named query.
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:<pre>{@code
 * class MyVaultNamedQueryCollector : VaultNamedQueryCollector<MyPojo, Int> {
 *
 *     override fun collect(resultSet: List<MyPojo>, parameters: Map<String, Any>): VaultNamedQueryCollector.Result<Int> {
 *         return VaultNamedQueryCollector.Result(listOf(resultSet.maxOf { it.value }), false)
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MyVaultNamedQueryCollector implements VaultNamedQueryCollector<Object, Integer> {
 *     @NotNull
 *     @Override
 *     public Result<Integer> collect(List<Object> resultSet, Map<String, Object> parameters) {
 *         return new VaultNamedQueryCollector.Result<>(
 *                 List.of(
 *                         resultSet.stream()
 *                                 .mapToInt(e -> Integer.parseInt(e.toString()))
 *                                 .max()
 *                                 .getAsInt()
 *                 ),
 *                 false);
 *     }
 * }
 * }</pre></li></ul>
 * 
 * @param <R> Type of the original result set.
 * @param <T> Type of the end result.
 */
public interface VaultNamedQueryCollector<R, T> {

    /**
     * @param resultSet The original result set that was returned by the named query execution.
     * @param parameters Parameters that were present in the query.
     *
     * @return A result that the original result set was collected into.
     */
    @NotNull
    Result<T> collect(@NotNull List<R> resultSet, @NotNull Map<String, Object> parameters);

    /**
     * Representation of a "collected" result set that also contains a flag that shows whether the result set is finished
     * or there are still elements in the original result set.
     * @param <T> Type of the records stored inside this result set.
     */
    class Result<T> {
        private List<T> results;
        private Boolean isDone;

        /**
         * @return The records in the result set.
         */
        @NotNull
        public List<T> getResults() {
            return results;
        }

        /**
         * @return Whether the result set has finished or not.
         */
        @NotNull
        public Boolean getDone() {
            return isDone;
        }

        public Result(@NotNull List<T> results, @NotNull Boolean isDone) {
            this.results = results;
            this.isDone = isDone;
        }
    }
}
