/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.p2p;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class Step2Message extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -3485720643965640091L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"Step2Message\",\"namespace\":\"net.corda.p2p\",\"fields\":[{\"name\":\"initiatorHello\",\"type\":{\"type\":\"record\",\"name\":\"InitiatorHelloMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":{\"type\":\"record\",\"name\":\"CommonHeader\",\"fields\":[{\"name\":\"messageType\",\"type\":{\"type\":\"enum\",\"name\":\"MessageType\",\"symbols\":[\"INITIATOR_HELLO\",\"RESPONDER_HELLO\",\"INITIATOR_HANDSHAKE\",\"RESPONDER_HANDSHAKE\",\"DATA\"]}},{\"name\":\"protocolVersion\",\"type\":\"int\"},{\"name\":\"sessionId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"sequenceNo\",\"type\":\"long\"},{\"name\":\"timestamp\",\"type\":\"long\"}]}},{\"name\":\"initiatorPublicKey\",\"type\":\"bytes\"},{\"name\":\"supportedModes\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"enum\",\"name\":\"ProtocolMode\",\"symbols\":[\"AUTHENTICATION_ONLY\",\"AUTHENTICATED_ENCRYPTION\"]}}}]}},{\"name\":\"responderHello\",\"type\":{\"type\":\"record\",\"name\":\"ResponderHelloMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"responderPublicKey\",\"type\":\"bytes\"},{\"name\":\"selectedMode\",\"type\":\"ProtocolMode\"}]}},{\"name\":\"privateKey\",\"type\":\"bytes\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<Step2Message> ENCODER =
      new BinaryMessageEncoder<Step2Message>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<Step2Message> DECODER =
      new BinaryMessageDecoder<Step2Message>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<Step2Message> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<Step2Message> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<Step2Message> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<Step2Message>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this Step2Message to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a Step2Message from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a Step2Message instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static Step2Message fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.p2p.crypto.InitiatorHelloMessage initiatorHello;
   private net.corda.p2p.crypto.ResponderHelloMessage responderHello;
   private java.nio.ByteBuffer privateKey;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public Step2Message() {}

  /**
   * All-args constructor.
   * @param initiatorHello The new value for initiatorHello
   * @param responderHello The new value for responderHello
   * @param privateKey The new value for privateKey
   */
  public Step2Message(net.corda.p2p.crypto.InitiatorHelloMessage initiatorHello, net.corda.p2p.crypto.ResponderHelloMessage responderHello, java.nio.ByteBuffer privateKey) {
    this.initiatorHello = initiatorHello;
    this.responderHello = responderHello;
    this.privateKey = privateKey;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return initiatorHello;
    case 1: return responderHello;
    case 2: return privateKey;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: initiatorHello = (net.corda.p2p.crypto.InitiatorHelloMessage)value$; break;
    case 1: responderHello = (net.corda.p2p.crypto.ResponderHelloMessage)value$; break;
    case 2: privateKey = (java.nio.ByteBuffer)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'initiatorHello' field.
   * @return The value of the 'initiatorHello' field.
   */
  public net.corda.p2p.crypto.InitiatorHelloMessage getInitiatorHello() {
    return initiatorHello;
  }


  /**
   * Sets the value of the 'initiatorHello' field.
   * @param value the value to set.
   */
  public void setInitiatorHello(net.corda.p2p.crypto.InitiatorHelloMessage value) {
    this.initiatorHello = value;
  }

  /**
   * Gets the value of the 'responderHello' field.
   * @return The value of the 'responderHello' field.
   */
  public net.corda.p2p.crypto.ResponderHelloMessage getResponderHello() {
    return responderHello;
  }


  /**
   * Sets the value of the 'responderHello' field.
   * @param value the value to set.
   */
  public void setResponderHello(net.corda.p2p.crypto.ResponderHelloMessage value) {
    this.responderHello = value;
  }

  /**
   * Gets the value of the 'privateKey' field.
   * @return The value of the 'privateKey' field.
   */
  public java.nio.ByteBuffer getPrivateKey() {
    return privateKey;
  }


  /**
   * Sets the value of the 'privateKey' field.
   * @param value the value to set.
   */
  public void setPrivateKey(java.nio.ByteBuffer value) {
    this.privateKey = value;
  }

  /**
   * Creates a new Step2Message RecordBuilder.
   * @return A new Step2Message RecordBuilder
   */
  public static net.corda.p2p.Step2Message.Builder newBuilder() {
    return new net.corda.p2p.Step2Message.Builder();
  }

  /**
   * Creates a new Step2Message RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new Step2Message RecordBuilder
   */
  public static net.corda.p2p.Step2Message.Builder newBuilder(net.corda.p2p.Step2Message.Builder other) {
    if (other == null) {
      return new net.corda.p2p.Step2Message.Builder();
    } else {
      return new net.corda.p2p.Step2Message.Builder(other);
    }
  }

  /**
   * Creates a new Step2Message RecordBuilder by copying an existing Step2Message instance.
   * @param other The existing instance to copy.
   * @return A new Step2Message RecordBuilder
   */
  public static net.corda.p2p.Step2Message.Builder newBuilder(net.corda.p2p.Step2Message other) {
    if (other == null) {
      return new net.corda.p2p.Step2Message.Builder();
    } else {
      return new net.corda.p2p.Step2Message.Builder(other);
    }
  }

  /**
   * RecordBuilder for Step2Message instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<Step2Message>
    implements org.apache.avro.data.RecordBuilder<Step2Message> {

    private net.corda.p2p.crypto.InitiatorHelloMessage initiatorHello;
    private net.corda.p2p.crypto.InitiatorHelloMessage.Builder initiatorHelloBuilder;
    private net.corda.p2p.crypto.ResponderHelloMessage responderHello;
    private net.corda.p2p.crypto.ResponderHelloMessage.Builder responderHelloBuilder;
    private java.nio.ByteBuffer privateKey;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.Step2Message.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.initiatorHello)) {
        this.initiatorHello = data().deepCopy(fields()[0].schema(), other.initiatorHello);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasInitiatorHelloBuilder()) {
        this.initiatorHelloBuilder = net.corda.p2p.crypto.InitiatorHelloMessage.newBuilder(other.getInitiatorHelloBuilder());
      }
      if (isValidValue(fields()[1], other.responderHello)) {
        this.responderHello = data().deepCopy(fields()[1].schema(), other.responderHello);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (other.hasResponderHelloBuilder()) {
        this.responderHelloBuilder = net.corda.p2p.crypto.ResponderHelloMessage.newBuilder(other.getResponderHelloBuilder());
      }
      if (isValidValue(fields()[2], other.privateKey)) {
        this.privateKey = data().deepCopy(fields()[2].schema(), other.privateKey);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
    }

    /**
     * Creates a Builder by copying an existing Step2Message instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.Step2Message other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.initiatorHello)) {
        this.initiatorHello = data().deepCopy(fields()[0].schema(), other.initiatorHello);
        fieldSetFlags()[0] = true;
      }
      this.initiatorHelloBuilder = null;
      if (isValidValue(fields()[1], other.responderHello)) {
        this.responderHello = data().deepCopy(fields()[1].schema(), other.responderHello);
        fieldSetFlags()[1] = true;
      }
      this.responderHelloBuilder = null;
      if (isValidValue(fields()[2], other.privateKey)) {
        this.privateKey = data().deepCopy(fields()[2].schema(), other.privateKey);
        fieldSetFlags()[2] = true;
      }
    }

    /**
      * Gets the value of the 'initiatorHello' field.
      * @return The value.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage getInitiatorHello() {
      return initiatorHello;
    }


    /**
      * Sets the value of the 'initiatorHello' field.
      * @param value The value of 'initiatorHello'.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder setInitiatorHello(net.corda.p2p.crypto.InitiatorHelloMessage value) {
      validate(fields()[0], value);
      this.initiatorHelloBuilder = null;
      this.initiatorHello = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'initiatorHello' field has been set.
      * @return True if the 'initiatorHello' field has been set, false otherwise.
      */
    public boolean hasInitiatorHello() {
      return fieldSetFlags()[0];
    }

    /**
     * Gets the Builder instance for the 'initiatorHello' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder getInitiatorHelloBuilder() {
      if (initiatorHelloBuilder == null) {
        if (hasInitiatorHello()) {
          setInitiatorHelloBuilder(net.corda.p2p.crypto.InitiatorHelloMessage.newBuilder(initiatorHello));
        } else {
          setInitiatorHelloBuilder(net.corda.p2p.crypto.InitiatorHelloMessage.newBuilder());
        }
      }
      return initiatorHelloBuilder;
    }

    /**
     * Sets the Builder instance for the 'initiatorHello' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.Step2Message.Builder setInitiatorHelloBuilder(net.corda.p2p.crypto.InitiatorHelloMessage.Builder value) {
      clearInitiatorHello();
      initiatorHelloBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'initiatorHello' field has an active Builder instance
     * @return True if the 'initiatorHello' field has an active Builder instance
     */
    public boolean hasInitiatorHelloBuilder() {
      return initiatorHelloBuilder != null;
    }

    /**
      * Clears the value of the 'initiatorHello' field.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder clearInitiatorHello() {
      initiatorHello = null;
      initiatorHelloBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'responderHello' field.
      * @return The value.
      */
    public net.corda.p2p.crypto.ResponderHelloMessage getResponderHello() {
      return responderHello;
    }


    /**
      * Sets the value of the 'responderHello' field.
      * @param value The value of 'responderHello'.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder setResponderHello(net.corda.p2p.crypto.ResponderHelloMessage value) {
      validate(fields()[1], value);
      this.responderHelloBuilder = null;
      this.responderHello = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'responderHello' field has been set.
      * @return True if the 'responderHello' field has been set, false otherwise.
      */
    public boolean hasResponderHello() {
      return fieldSetFlags()[1];
    }

    /**
     * Gets the Builder instance for the 'responderHello' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.p2p.crypto.ResponderHelloMessage.Builder getResponderHelloBuilder() {
      if (responderHelloBuilder == null) {
        if (hasResponderHello()) {
          setResponderHelloBuilder(net.corda.p2p.crypto.ResponderHelloMessage.newBuilder(responderHello));
        } else {
          setResponderHelloBuilder(net.corda.p2p.crypto.ResponderHelloMessage.newBuilder());
        }
      }
      return responderHelloBuilder;
    }

    /**
     * Sets the Builder instance for the 'responderHello' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.Step2Message.Builder setResponderHelloBuilder(net.corda.p2p.crypto.ResponderHelloMessage.Builder value) {
      clearResponderHello();
      responderHelloBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'responderHello' field has an active Builder instance
     * @return True if the 'responderHello' field has an active Builder instance
     */
    public boolean hasResponderHelloBuilder() {
      return responderHelloBuilder != null;
    }

    /**
      * Clears the value of the 'responderHello' field.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder clearResponderHello() {
      responderHello = null;
      responderHelloBuilder = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'privateKey' field.
      * @return The value.
      */
    public java.nio.ByteBuffer getPrivateKey() {
      return privateKey;
    }


    /**
      * Sets the value of the 'privateKey' field.
      * @param value The value of 'privateKey'.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder setPrivateKey(java.nio.ByteBuffer value) {
      validate(fields()[2], value);
      this.privateKey = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'privateKey' field has been set.
      * @return True if the 'privateKey' field has been set, false otherwise.
      */
    public boolean hasPrivateKey() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'privateKey' field.
      * @return This builder.
      */
    public net.corda.p2p.Step2Message.Builder clearPrivateKey() {
      privateKey = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Step2Message build() {
      try {
        Step2Message record = new Step2Message();
        if (initiatorHelloBuilder != null) {
          try {
            record.initiatorHello = this.initiatorHelloBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("initiatorHello"));
            throw e;
          }
        } else {
          record.initiatorHello = fieldSetFlags()[0] ? this.initiatorHello : (net.corda.p2p.crypto.InitiatorHelloMessage) defaultValue(fields()[0]);
        }
        if (responderHelloBuilder != null) {
          try {
            record.responderHello = this.responderHelloBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("responderHello"));
            throw e;
          }
        } else {
          record.responderHello = fieldSetFlags()[1] ? this.responderHello : (net.corda.p2p.crypto.ResponderHelloMessage) defaultValue(fields()[1]);
        }
        record.privateKey = fieldSetFlags()[2] ? this.privateKey : (java.nio.ByteBuffer) defaultValue(fields()[2]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<Step2Message>
    WRITER$ = (org.apache.avro.io.DatumWriter<Step2Message>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<Step2Message>
    READER$ = (org.apache.avro.io.DatumReader<Step2Message>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.initiatorHello.customEncode(out);

    this.responderHello.customEncode(out);

    out.writeBytes(this.privateKey);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.initiatorHello == null) {
        this.initiatorHello = new net.corda.p2p.crypto.InitiatorHelloMessage();
      }
      this.initiatorHello.customDecode(in);

      if (this.responderHello == null) {
        this.responderHello = new net.corda.p2p.crypto.ResponderHelloMessage();
      }
      this.responderHello.customDecode(in);

      this.privateKey = in.readBytes(this.privateKey);

    } else {
      for (int i = 0; i < 3; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.initiatorHello == null) {
            this.initiatorHello = new net.corda.p2p.crypto.InitiatorHelloMessage();
          }
          this.initiatorHello.customDecode(in);
          break;

        case 1:
          if (this.responderHello == null) {
            this.responderHello = new net.corda.p2p.crypto.ResponderHelloMessage();
          }
          this.responderHello.customDecode(in);
          break;

        case 2:
          this.privateKey = in.readBytes(this.privateKey);
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










