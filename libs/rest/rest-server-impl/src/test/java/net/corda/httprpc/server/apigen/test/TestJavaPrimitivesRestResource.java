package net.corda.httprpc.server.apigen.test;

import net.corda.httprpc.RestResource;
import net.corda.httprpc.annotations.HttpGET;
import net.corda.httprpc.annotations.HttpPOST;
import net.corda.httprpc.annotations.RestPathParameter;
import net.corda.httprpc.annotations.RestQueryParameter;
import net.corda.httprpc.annotations.ClientRequestBodyParameter;
import net.corda.httprpc.annotations.HttpRestResource;

@HttpRestResource(
    name = "API",
    description = "Java Test",
    path = "java"
)
public interface TestJavaPrimitivesRestResource extends RestResource {

  @HttpPOST(
      path = "negateInteger",
      title = "Negate Integer",
      description = "Negate an Integer"
  )
  Integer negateInt(
      @ClientRequestBodyParameter(
          description = "Int",
          required = false
      ) Integer number
  );

  @HttpPOST(
      path = "negatePrimitiveInteger",
      title = "Negate Integer",
      description = "Negate an Integer"
  )
  int negatePrimitiveInt(
      @ClientRequestBodyParameter(
          description = "int",
          required = false
      ) int number
  );

  @HttpGET(
      path = "negate_long",
      title = "Negate Long",
      description = "Negate a Long value"
  )
  Long negateLong(@RestQueryParameter Long number);

  @HttpGET(
      path = "negate_boolean",
      title = "Negate Boolean",
      description = "Negate a Boolean value"
  )
  Boolean negateBoolean(@RestQueryParameter Boolean bool);

  @HttpGET(
      path = "reverse/{text}",
      title = "Reverse Text",
      description = "Reverse a text"
  )
  String reverse(
      @RestPathParameter(
          name = "text",
          description = "The text to reverse"
      ) String text
  );
}
