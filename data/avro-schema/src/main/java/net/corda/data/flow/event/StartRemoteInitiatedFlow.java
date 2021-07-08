/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.data.flow.event;

import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.specific.SpecificData;

@org.apache.avro.specific.AvroGenerated
public class StartRemoteInitiatedFlow extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 2685995226450598235L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"StartRemoteInitiatedFlow\",\"namespace\":\"net.corda.data.flow.event\",\"fields\":[{\"name\":\"message\",\"type\":{\"type\":\"record\",\"name\":\"P2PMessage\",\"fields\":[{\"name\":\"flowName\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"source\",\"type\":{\"type\":\"record\",\"name\":\"Identity\",\"namespace\":\"net.corda.data.flow\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"group\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}]}]}},{\"name\":\"destination\",\"type\":\"net.corda.data.flow.Identity\"},{\"name\":\"sessionId\",\"type\":\"bytes\"},{\"name\":\"sequenceNo\",\"type\":\"int\"},{\"name\":\"message\",\"type\":\"bytes\"}]}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<StartRemoteInitiatedFlow> ENCODER =
      new BinaryMessageEncoder<StartRemoteInitiatedFlow>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<StartRemoteInitiatedFlow> DECODER =
      new BinaryMessageDecoder<StartRemoteInitiatedFlow>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<StartRemoteInitiatedFlow> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<StartRemoteInitiatedFlow> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<StartRemoteInitiatedFlow> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<StartRemoteInitiatedFlow>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this StartRemoteInitiatedFlow to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a StartRemoteInitiatedFlow from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a StartRemoteInitiatedFlow instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static StartRemoteInitiatedFlow fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.data.flow.event.P2PMessage message;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public StartRemoteInitiatedFlow() {}

  /**
   * All-args constructor.
   * @param message The new value for message
   */
  public StartRemoteInitiatedFlow(net.corda.data.flow.event.P2PMessage message) {
    this.message = message;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return message;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: message = (net.corda.data.flow.event.P2PMessage)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'message' field.
   * @return The value of the 'message' field.
   */
  public net.corda.data.flow.event.P2PMessage getMessage() {
    return message;
  }


  /**
   * Sets the value of the 'message' field.
   * @param value the value to set.
   */
  public void setMessage(net.corda.data.flow.event.P2PMessage value) {
    this.message = value;
  }

  /**
   * Creates a new StartRemoteInitiatedFlow RecordBuilder.
   * @return A new StartRemoteInitiatedFlow RecordBuilder
   */
  public static net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder newBuilder() {
    return new net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder();
  }

  /**
   * Creates a new StartRemoteInitiatedFlow RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new StartRemoteInitiatedFlow RecordBuilder
   */
  public static net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder newBuilder(net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder other) {
    if (other == null) {
      return new net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder();
    } else {
      return new net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder(other);
    }
  }

  /**
   * Creates a new StartRemoteInitiatedFlow RecordBuilder by copying an existing StartRemoteInitiatedFlow instance.
   * @param other The existing instance to copy.
   * @return A new StartRemoteInitiatedFlow RecordBuilder
   */
  public static net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder newBuilder(net.corda.data.flow.event.StartRemoteInitiatedFlow other) {
    if (other == null) {
      return new net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder();
    } else {
      return new net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder(other);
    }
  }

  /**
   * RecordBuilder for StartRemoteInitiatedFlow instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<StartRemoteInitiatedFlow>
    implements org.apache.avro.data.RecordBuilder<StartRemoteInitiatedFlow> {

    private net.corda.data.flow.event.P2PMessage message;
    private net.corda.data.flow.event.P2PMessage.Builder messageBuilder;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.message)) {
        this.message = data().deepCopy(fields()[0].schema(), other.message);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasMessageBuilder()) {
        this.messageBuilder = net.corda.data.flow.event.P2PMessage.newBuilder(other.getMessageBuilder());
      }
    }

    /**
     * Creates a Builder by copying an existing StartRemoteInitiatedFlow instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.data.flow.event.StartRemoteInitiatedFlow other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.message)) {
        this.message = data().deepCopy(fields()[0].schema(), other.message);
        fieldSetFlags()[0] = true;
      }
      this.messageBuilder = null;
    }

    /**
      * Gets the value of the 'message' field.
      * @return The value.
      */
    public net.corda.data.flow.event.P2PMessage getMessage() {
      return message;
    }


    /**
      * Sets the value of the 'message' field.
      * @param value The value of 'message'.
      * @return This builder.
      */
    public net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder setMessage(net.corda.data.flow.event.P2PMessage value) {
      validate(fields()[0], value);
      this.messageBuilder = null;
      this.message = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'message' field has been set.
      * @return True if the 'message' field has been set, false otherwise.
      */
    public boolean hasMessage() {
      return fieldSetFlags()[0];
    }

    /**
     * Gets the Builder instance for the 'message' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.data.flow.event.P2PMessage.Builder getMessageBuilder() {
      if (messageBuilder == null) {
        if (hasMessage()) {
          setMessageBuilder(net.corda.data.flow.event.P2PMessage.newBuilder(message));
        } else {
          setMessageBuilder(net.corda.data.flow.event.P2PMessage.newBuilder());
        }
      }
      return messageBuilder;
    }

    /**
     * Sets the Builder instance for the 'message' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder setMessageBuilder(net.corda.data.flow.event.P2PMessage.Builder value) {
      clearMessage();
      messageBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'message' field has an active Builder instance
     * @return True if the 'message' field has an active Builder instance
     */
    public boolean hasMessageBuilder() {
      return messageBuilder != null;
    }

    /**
      * Clears the value of the 'message' field.
      * @return This builder.
      */
    public net.corda.data.flow.event.StartRemoteInitiatedFlow.Builder clearMessage() {
      message = null;
      messageBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StartRemoteInitiatedFlow build() {
      try {
        StartRemoteInitiatedFlow record = new StartRemoteInitiatedFlow();
        if (messageBuilder != null) {
          try {
            record.message = this.messageBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("message"));
            throw e;
          }
        } else {
          record.message = fieldSetFlags()[0] ? this.message : (net.corda.data.flow.event.P2PMessage) defaultValue(fields()[0]);
        }
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<StartRemoteInitiatedFlow>
    WRITER$ = (org.apache.avro.io.DatumWriter<StartRemoteInitiatedFlow>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<StartRemoteInitiatedFlow>
    READER$ = (org.apache.avro.io.DatumReader<StartRemoteInitiatedFlow>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.message.customEncode(out);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.message == null) {
        this.message = new net.corda.data.flow.event.P2PMessage();
      }
      this.message.customDecode(in);

    } else {
      for (int i = 0; i < 1; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.message == null) {
            this.message = new net.corda.data.flow.event.P2PMessage();
          }
          this.message.customDecode(in);
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










