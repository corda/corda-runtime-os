package net.corda.httprpc.server.apigen.test;

import net.corda.httprpc.RestResource;
import net.corda.httprpc.annotations.HttpRpcGET;
import net.corda.httprpc.annotations.HttpRpcPOST;
import net.corda.httprpc.annotations.HttpRpcPathParameter;
import net.corda.httprpc.annotations.HttpRpcQueryParameter;
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter;
import net.corda.httprpc.annotations.HttpRpcResource;

@HttpRpcResource(
    name = "API",
    description = "Java Test",
    path = "java"
)
public interface TestJavaPrimitivesRestResource extends RestResource {

  @HttpRpcPOST(
      path = "negateInteger",
      title = "Negate Integer",
      description = "Negate an Integer"
  )
  Integer negateInt(
      @HttpRpcRequestBodyParameter(
          description = "Int",
          required = false
      ) Integer number
  );

  @HttpRpcPOST(
      path = "negatePrimitiveInteger",
      title = "Negate Integer",
      description = "Negate an Integer"
  )
  int negatePrimitiveInt(
      @HttpRpcRequestBodyParameter(
          description = "int",
          required = false
      ) int number
  );

  @HttpRpcGET(
      path = "negate_long",
      title = "Negate Long",
      description = "Negate a Long value"
  )
  Long negateLong(@HttpRpcQueryParameter Long number);

  @HttpRpcGET(
      path = "negate_boolean",
      title = "Negate Boolean",
      description = "Negate a Boolean value"
  )
  Boolean negateBoolean(@HttpRpcQueryParameter Boolean bool);

  @HttpRpcGET(
      path = "reverse/{text}",
      title = "Reverse Text",
      description = "Reverse a text"
  )
  String reverse(
      @HttpRpcPathParameter(
          name = "text",
          description = "The text to reverse"
      ) String text
  );
}
