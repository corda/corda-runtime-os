import net.corda.data.models.State

interface StateManager {
    fun <V: Any> get(key: String): State<V>
    fun <V: Any> store(state: State<V>)
    fun delete(key: String)
}