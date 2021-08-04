/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.flow.event;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class RemoteFlowError extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 6988159407006502949L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"RemoteFlowError\",\"namespace\":\"net.corda.data.flow.event\",\"fields\":[{\"name\":\"flowName\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"source\",\"type\":{\"type\":\"record\",\"name\":\"HoldingIdentity\",\"namespace\":\"net.corda.data.identity\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"groupId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"destination\",\"type\":\"net.corda.data.identity.HoldingIdentity\"},{\"name\":\"sessionId\",\"type\":\"bytes\"},{\"name\":\"errorMessage\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<RemoteFlowError> ENCODER =
      new BinaryMessageEncoder<RemoteFlowError>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<RemoteFlowError> DECODER =
      new BinaryMessageDecoder<RemoteFlowError>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<RemoteFlowError> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<RemoteFlowError> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<RemoteFlowError> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<RemoteFlowError>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this RemoteFlowError to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a RemoteFlowError from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a RemoteFlowError instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static RemoteFlowError fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private java.lang.String flowName;
   private net.corda.data.identity.HoldingIdentity source;
   private net.corda.data.identity.HoldingIdentity destination;
   private java.nio.ByteBuffer sessionId;
   private java.lang.String errorMessage;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public RemoteFlowError() {}

  /**
   * All-args constructor.
   * @param flowName The new value for flowName
   * @param source The new value for source
   * @param destination The new value for destination
   * @param sessionId The new value for sessionId
   * @param errorMessage The new value for errorMessage
   */
  public RemoteFlowError(java.lang.String flowName, net.corda.data.identity.HoldingIdentity source, net.corda.data.identity.HoldingIdentity destination, java.nio.ByteBuffer sessionId, java.lang.String errorMessage) {
    this.flowName = flowName;
    this.source = source;
    this.destination = destination;
    this.sessionId = sessionId;
    this.errorMessage = errorMessage;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return flowName;
    case 1: return source;
    case 2: return destination;
    case 3: return sessionId;
    case 4: return errorMessage;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: flowName = value$ != null ? value$.toString() : null; break;
    case 1: source = (net.corda.data.identity.HoldingIdentity)value$; break;
    case 2: destination = (net.corda.data.identity.HoldingIdentity)value$; break;
    case 3: sessionId = (java.nio.ByteBuffer)value$; break;
    case 4: errorMessage = value$ != null ? value$.toString() : null; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'flowName' field.
   * @return The value of the 'flowName' field.
   */
  public java.lang.String getFlowName() {
    return flowName;
  }


  /**
   * Sets the value of the 'flowName' field.
   * @param value the value to set.
   */
  public void setFlowName(java.lang.String value) {
    this.flowName = value;
  }

  /**
   * Gets the value of the 'source' field.
   * @return The value of the 'source' field.
   */
  public net.corda.data.identity.HoldingIdentity getSource() {
    return source;
  }


  /**
   * Sets the value of the 'source' field.
   * @param value the value to set.
   */
  public void setSource(net.corda.data.identity.HoldingIdentity value) {
    this.source = value;
  }

  /**
   * Gets the value of the 'destination' field.
   * @return The value of the 'destination' field.
   */
  public net.corda.data.identity.HoldingIdentity getDestination() {
    return destination;
  }


  /**
   * Sets the value of the 'destination' field.
   * @param value the value to set.
   */
  public void setDestination(net.corda.data.identity.HoldingIdentity value) {
    this.destination = value;
  }

  /**
   * Gets the value of the 'sessionId' field.
   * @return The value of the 'sessionId' field.
   */
  public java.nio.ByteBuffer getSessionId() {
    return sessionId;
  }


  /**
   * Sets the value of the 'sessionId' field.
   * @param value the value to set.
   */
  public void setSessionId(java.nio.ByteBuffer value) {
    this.sessionId = value;
  }

  /**
   * Gets the value of the 'errorMessage' field.
   * @return The value of the 'errorMessage' field.
   */
  public java.lang.String getErrorMessage() {
    return errorMessage;
  }


  /**
   * Sets the value of the 'errorMessage' field.
   * @param value the value to set.
   */
  public void setErrorMessage(java.lang.String value) {
    this.errorMessage = value;
  }

  /**
   * Creates a new RemoteFlowError RecordBuilder.
   * @return A new RemoteFlowError RecordBuilder
   */
  public static net.corda.data.flow.event.RemoteFlowError.Builder newBuilder() {
    return new net.corda.data.flow.event.RemoteFlowError.Builder();
  }

  /**
   * Creates a new RemoteFlowError RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new RemoteFlowError RecordBuilder
   */
  public static net.corda.data.flow.event.RemoteFlowError.Builder newBuilder(net.corda.data.flow.event.RemoteFlowError.Builder other) {
    if (other == null) {
      return new net.corda.data.flow.event.RemoteFlowError.Builder();
    } else {
      return new net.corda.data.flow.event.RemoteFlowError.Builder(other);
    }
  }

  /**
   * Creates a new RemoteFlowError RecordBuilder by copying an existing RemoteFlowError instance.
   * @param other The existing instance to copy.
   * @return A new RemoteFlowError RecordBuilder
   */
  public static net.corda.data.flow.event.RemoteFlowError.Builder newBuilder(net.corda.data.flow.event.RemoteFlowError other) {
    if (other == null) {
      return new net.corda.data.flow.event.RemoteFlowError.Builder();
    } else {
      return new net.corda.data.flow.event.RemoteFlowError.Builder(other);
    }
  }

  /**
   * RecordBuilder for RemoteFlowError instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<RemoteFlowError>
    implements org.apache.avro.data.RecordBuilder<RemoteFlowError> {

    private java.lang.String flowName;
    private net.corda.data.identity.HoldingIdentity source;
    private net.corda.data.identity.HoldingIdentity.Builder sourceBuilder;
    private net.corda.data.identity.HoldingIdentity destination;
    private net.corda.data.identity.HoldingIdentity.Builder destinationBuilder;
    private java.nio.ByteBuffer sessionId;
    private java.lang.String errorMessage;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.flow.event.RemoteFlowError.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.flowName)) {
        this.flowName = data().deepCopy(fields()[0].schema(), other.flowName);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (isValidValue(fields()[1], other.source)) {
        this.source = data().deepCopy(fields()[1].schema(), other.source);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (other.hasSourceBuilder()) {
        this.sourceBuilder = net.corda.data.identity.HoldingIdentity.newBuilder(other.getSourceBuilder());
      }
      if (isValidValue(fields()[2], other.destination)) {
        this.destination = data().deepCopy(fields()[2].schema(), other.destination);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
      if (other.hasDestinationBuilder()) {
        this.destinationBuilder = net.corda.data.identity.HoldingIdentity.newBuilder(other.getDestinationBuilder());
      }
      if (isValidValue(fields()[3], other.sessionId)) {
        this.sessionId = data().deepCopy(fields()[3].schema(), other.sessionId);
        fieldSetFlags()[3] = other.fieldSetFlags()[3];
      }
      if (isValidValue(fields()[4], other.errorMessage)) {
        this.errorMessage = data().deepCopy(fields()[4].schema(), other.errorMessage);
        fieldSetFlags()[4] = other.fieldSetFlags()[4];
      }
    }

    /**
     * Creates a Builder by copying an existing RemoteFlowError instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.flow.event.RemoteFlowError other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.flowName)) {
        this.flowName = data().deepCopy(fields()[0].schema(), other.flowName);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.source)) {
        this.source = data().deepCopy(fields()[1].schema(), other.source);
        fieldSetFlags()[1] = true;
      }
      this.sourceBuilder = null;
      if (isValidValue(fields()[2], other.destination)) {
        this.destination = data().deepCopy(fields()[2].schema(), other.destination);
        fieldSetFlags()[2] = true;
      }
      this.destinationBuilder = null;
      if (isValidValue(fields()[3], other.sessionId)) {
        this.sessionId = data().deepCopy(fields()[3].schema(), other.sessionId);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.errorMessage)) {
        this.errorMessage = data().deepCopy(fields()[4].schema(), other.errorMessage);
        fieldSetFlags()[4] = true;
      }
    }

    /**
      * Gets the value of the 'flowName' field.
      * @return The value.
      */
    public java.lang.String getFlowName() {
      return flowName;
    }


    /**
      * Sets the value of the 'flowName' field.
      * @param value The value of 'flowName'.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder setFlowName(java.lang.String value) {
      validate(fields()[0], value);
      this.flowName = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'flowName' field has been set.
      * @return True if the 'flowName' field has been set, false otherwise.
      */
    public boolean hasFlowName() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'flowName' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder clearFlowName() {
      flowName = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'source' field.
      * @return The value.
      */
    public net.corda.data.identity.HoldingIdentity getSource() {
      return source;
    }


    /**
      * Sets the value of the 'source' field.
      * @param value The value of 'source'.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder setSource(net.corda.data.identity.HoldingIdentity value) {
      validate(fields()[1], value);
      this.sourceBuilder = null;
      this.source = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'source' field has been set.
      * @return True if the 'source' field has been set, false otherwise.
      */
    public boolean hasSource() {
      return fieldSetFlags()[1];
    }

    /**
     * Gets the Builder instance for the 'source' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.data.identity.HoldingIdentity.Builder getSourceBuilder() {
      if (sourceBuilder == null) {
        if (hasSource()) {
          setSourceBuilder(net.corda.data.identity.HoldingIdentity.newBuilder(source));
        } else {
          setSourceBuilder(net.corda.data.identity.HoldingIdentity.newBuilder());
        }
      }
      return sourceBuilder;
    }

    /**
     * Sets the Builder instance for the 'source' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.data.flow.event.RemoteFlowError.Builder setSourceBuilder(net.corda.data.identity.HoldingIdentity.Builder value) {
      clearSource();
      sourceBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'source' field has an active Builder instance
     * @return True if the 'source' field has an active Builder instance
     */
    public boolean hasSourceBuilder() {
      return sourceBuilder != null;
    }

    /**
      * Clears the value of the 'source' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder clearSource() {
      source = null;
      sourceBuilder = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'destination' field.
      * @return The value.
      */
    public net.corda.data.identity.HoldingIdentity getDestination() {
      return destination;
    }


    /**
      * Sets the value of the 'destination' field.
      * @param value The value of 'destination'.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder setDestination(net.corda.data.identity.HoldingIdentity value) {
      validate(fields()[2], value);
      this.destinationBuilder = null;
      this.destination = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'destination' field has been set.
      * @return True if the 'destination' field has been set, false otherwise.
      */
    public boolean hasDestination() {
      return fieldSetFlags()[2];
    }

    /**
     * Gets the Builder instance for the 'destination' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.data.identity.HoldingIdentity.Builder getDestinationBuilder() {
      if (destinationBuilder == null) {
        if (hasDestination()) {
          setDestinationBuilder(net.corda.data.identity.HoldingIdentity.newBuilder(destination));
        } else {
          setDestinationBuilder(net.corda.data.identity.HoldingIdentity.newBuilder());
        }
      }
      return destinationBuilder;
    }

    /**
     * Sets the Builder instance for the 'destination' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.data.flow.event.RemoteFlowError.Builder setDestinationBuilder(net.corda.data.identity.HoldingIdentity.Builder value) {
      clearDestination();
      destinationBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'destination' field has an active Builder instance
     * @return True if the 'destination' field has an active Builder instance
     */
    public boolean hasDestinationBuilder() {
      return destinationBuilder != null;
    }

    /**
      * Clears the value of the 'destination' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder clearDestination() {
      destination = null;
      destinationBuilder = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /**
      * Gets the value of the 'sessionId' field.
      * @return The value.
      */
    public java.nio.ByteBuffer getSessionId() {
      return sessionId;
    }


    /**
      * Sets the value of the 'sessionId' field.
      * @param value The value of 'sessionId'.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder setSessionId(java.nio.ByteBuffer value) {
      validate(fields()[3], value);
      this.sessionId = value;
      fieldSetFlags()[3] = true;
      return this;
    }

    /**
      * Checks whether the 'sessionId' field has been set.
      * @return True if the 'sessionId' field has been set, false otherwise.
      */
    public boolean hasSessionId() {
      return fieldSetFlags()[3];
    }


    /**
      * Clears the value of the 'sessionId' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder clearSessionId() {
      sessionId = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /**
      * Gets the value of the 'errorMessage' field.
      * @return The value.
      */
    public java.lang.String getErrorMessage() {
      return errorMessage;
    }


    /**
      * Sets the value of the 'errorMessage' field.
      * @param value The value of 'errorMessage'.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder setErrorMessage(java.lang.String value) {
      validate(fields()[4], value);
      this.errorMessage = value;
      fieldSetFlags()[4] = true;
      return this;
    }

    /**
      * Checks whether the 'errorMessage' field has been set.
      * @return True if the 'errorMessage' field has been set, false otherwise.
      */
    public boolean hasErrorMessage() {
      return fieldSetFlags()[4];
    }


    /**
      * Clears the value of the 'errorMessage' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.RemoteFlowError.Builder clearErrorMessage() {
      errorMessage = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RemoteFlowError build() {
      try {
        RemoteFlowError record = new RemoteFlowError();
        record.flowName = fieldSetFlags()[0] ? this.flowName : (java.lang.String) defaultValue(fields()[0]);
        if (sourceBuilder != null) {
          try {
            record.source = this.sourceBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("source"));
            throw e;
          }
        } else {
          record.source = fieldSetFlags()[1] ? this.source : (net.corda.data.identity.HoldingIdentity) defaultValue(fields()[1]);
        }
        if (destinationBuilder != null) {
          try {
            record.destination = this.destinationBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("destination"));
            throw e;
          }
        } else {
          record.destination = fieldSetFlags()[2] ? this.destination : (net.corda.data.identity.HoldingIdentity) defaultValue(fields()[2]);
        }
        record.sessionId = fieldSetFlags()[3] ? this.sessionId : (java.nio.ByteBuffer) defaultValue(fields()[3]);
        record.errorMessage = fieldSetFlags()[4] ? this.errorMessage : (java.lang.String) defaultValue(fields()[4]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<RemoteFlowError>
    WRITER$ = (org.apache.avro.io.DatumWriter<RemoteFlowError>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<RemoteFlowError>
    READER$ = (org.apache.avro.io.DatumReader<RemoteFlowError>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    out.writeString(this.flowName);

    this.source.customEncode(out);

    this.destination.customEncode(out);

    out.writeBytes(this.sessionId);

    out.writeString(this.errorMessage);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      this.flowName = in.readString();

      if (this.source == null) {
        this.source = new net.corda.data.identity.HoldingIdentity();
      }
      this.source.customDecode(in);

      if (this.destination == null) {
        this.destination = new net.corda.data.identity.HoldingIdentity();
      }
      this.destination.customDecode(in);

      this.sessionId = in.readBytes(this.sessionId);

      this.errorMessage = in.readString();

    } else {
      for (int i = 0; i < 5; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          this.flowName = in.readString();
          break;

        case 1:
          if (this.source == null) {
            this.source = new net.corda.data.identity.HoldingIdentity();
          }
          this.source.customDecode(in);
          break;

        case 2:
          if (this.destination == null) {
            this.destination = new net.corda.data.identity.HoldingIdentity();
          }
          this.destination.customDecode(in);
          break;

        case 3:
          this.sessionId = in.readBytes(this.sessionId);
          break;

        case 4:
          this.errorMessage = in.readString();
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










