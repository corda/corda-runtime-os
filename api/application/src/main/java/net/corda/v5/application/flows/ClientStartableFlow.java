package net.corda.v5.application.flows;

import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ClientStartableFlow} is a {@link Flow} that is started via RPC.
 * <p>
 * {@link ClientStartableFlow#call} takes in a {@link ClientRequestBody}, containing the body of the RPC request that started the flow.
 * <p>
 * The string return type is treated by the platform as a JSON encoded string to return to the REST
 * service, and will appear in the REST flow status when the flow completes. To assist in returning valid JSON, the
 * {@link JsonMarshallingService} can be used.
 * <p>
 * Flows implementing this interface must have a no-arg constructor. The flow invocation will fail if this constructor
 * does not exist.
 * <p>
 * Example usage:
 * <ul>
 * <li>Kotlin:
 * <pre>{@code
 * class MyFlow : ClientStartableFlow {
 *
 *     @CordaInject
 *     lateinit var jsonMarshallingService: JsonMarshallingService
 *
 *     @Suspendable
 *     override fun call(requestBody: RestRequestBody): String {
 *         val parameters = requestBody.getRequestBodyAs<MyFlowParameters>(jsonMarshallingService)
 *         ...
 *         return jsonMarshallingService.format(parameters)
 *     }
 * }
 * }</pre></li>
 * <li>Java:<pre>{@code
 * public class MyFlow implements ClientStartableFlow {
 *
 *     @CordaInject
 *     public JsonMarshallingService jsonMarshallingService;
 *
 *     @Suspendable
 *     @Override
 *     public String call(RestRequestBody requestBody) {
 *         MyFlowParameters parameters = requestBody.getRequestBodyAs(jsonMarshallingService, MyFlowParameters.class);
 *         ...
 *         return jsonMarshallingService.format(parameters);
 *     }
 * }
 * }</pre></li></ul>
 */
public interface ClientStartableFlow extends Flow {

    /**
     * The business logic for this flow should be written here.
     * <p>
     * This is equivalent to the normal flow call method, where the output is fixed to being a JSON encoded string.
     * Additionally, the call method is invoked with the body of the RPC request.
     *
     * @param requestBody The body of the RPC request that started this flow.
     *
     * @return A JSON encoded string to be supplied to the flow status on flow completion as the result.
     */
    @Suspendable
    @NotNull
    String call(@NotNull ClientRequestBody requestBody);
}