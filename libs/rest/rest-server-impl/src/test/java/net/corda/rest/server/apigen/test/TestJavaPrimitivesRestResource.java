package net.corda.rest.server.apigen.test;

import net.corda.rest.RestResource;
import net.corda.rest.annotations.HttpGET;
import net.corda.rest.annotations.HttpPOST;
import net.corda.rest.annotations.RestPathParameter;
import net.corda.rest.annotations.RestQueryParameter;
import net.corda.rest.annotations.ClientRequestBodyParameter;
import net.corda.rest.annotations.HttpRestResource;

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
