/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package net.corda.p2p.crypto;

import org.apache.avro.generic.GenericArray;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.util.Utf8;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@org.apache.avro.specific.AvroGenerated
public class InitiatorHelloMessage extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 6341215738914951783L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"InitiatorHelloMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":{\"type\":\"record\",\"name\":\"CommonHeader\",\"fields\":[{\"name\":\"messageType\",\"type\":{\"type\":\"enum\",\"name\":\"MessageType\",\"symbols\":[\"INITIATOR_HELLO\",\"RESPONDER_HELLO\",\"INITIATOR_HANDSHAKE\",\"RESPONDER_HANDSHAKE\",\"DATA\"]}},{\"name\":\"protocolVersion\",\"type\":\"int\"},{\"name\":\"sessionId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"sequenceNo\",\"type\":\"long\"},{\"name\":\"timestamp\",\"type\":\"long\"}]}},{\"name\":\"initiatorPublicKey\",\"type\":\"bytes\"},{\"name\":\"supportedModes\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"enum\",\"name\":\"ProtocolMode\",\"symbols\":[\"AUTHENTICATION_ONLY\"]}}}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<InitiatorHelloMessage> ENCODER =
      new BinaryMessageEncoder<InitiatorHelloMessage>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<InitiatorHelloMessage> DECODER =
      new BinaryMessageDecoder<InitiatorHelloMessage>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<InitiatorHelloMessage> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<InitiatorHelloMessage> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<InitiatorHelloMessage> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<InitiatorHelloMessage>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this InitiatorHelloMessage to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a InitiatorHelloMessage from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a InitiatorHelloMessage instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static InitiatorHelloMessage fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.p2p.crypto.CommonHeader header;
   private java.nio.ByteBuffer initiatorPublicKey;
   private java.util.List<net.corda.p2p.crypto.ProtocolMode> supportedModes;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public InitiatorHelloMessage() {}

  /**
   * All-args constructor.
   * @param header The new value for header
   * @param initiatorPublicKey The new value for initiatorPublicKey
   * @param supportedModes The new value for supportedModes
   */
  public InitiatorHelloMessage(net.corda.p2p.crypto.CommonHeader header, java.nio.ByteBuffer initiatorPublicKey, java.util.List<net.corda.p2p.crypto.ProtocolMode> supportedModes) {
    this.header = header;
    this.initiatorPublicKey = initiatorPublicKey;
    this.supportedModes = supportedModes;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return header;
    case 1: return initiatorPublicKey;
    case 2: return supportedModes;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: header = (net.corda.p2p.crypto.CommonHeader)value$; break;
    case 1: initiatorPublicKey = (java.nio.ByteBuffer)value$; break;
    case 2: supportedModes = (java.util.List<net.corda.p2p.crypto.ProtocolMode>)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'header' field.
   * @return The value of the 'header' field.
   */
  public net.corda.p2p.crypto.CommonHeader getHeader() {
    return header;
  }


  /**
   * Sets the value of the 'header' field.
   * @param value the value to set.
   */
  public void setHeader(net.corda.p2p.crypto.CommonHeader value) {
    this.header = value;
  }

  /**
   * Gets the value of the 'initiatorPublicKey' field.
   * @return The value of the 'initiatorPublicKey' field.
   */
  public java.nio.ByteBuffer getInitiatorPublicKey() {
    return initiatorPublicKey;
  }


  /**
   * Sets the value of the 'initiatorPublicKey' field.
   * @param value the value to set.
   */
  public void setInitiatorPublicKey(java.nio.ByteBuffer value) {
    this.initiatorPublicKey = value;
  }

  /**
   * Gets the value of the 'supportedModes' field.
   * @return The value of the 'supportedModes' field.
   */
  public java.util.List<net.corda.p2p.crypto.ProtocolMode> getSupportedModes() {
    return supportedModes;
  }


  /**
   * Sets the value of the 'supportedModes' field.
   * @param value the value to set.
   */
  public void setSupportedModes(java.util.List<net.corda.p2p.crypto.ProtocolMode> value) {
    this.supportedModes = value;
  }

  /**
   * Creates a new InitiatorHelloMessage RecordBuilder.
   * @return A new InitiatorHelloMessage RecordBuilder
   */
  public static net.corda.p2p.crypto.InitiatorHelloMessage.Builder newBuilder() {
    return new net.corda.p2p.crypto.InitiatorHelloMessage.Builder();
  }

  /**
   * Creates a new InitiatorHelloMessage RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new InitiatorHelloMessage RecordBuilder
   */
  public static net.corda.p2p.crypto.InitiatorHelloMessage.Builder newBuilder(net.corda.p2p.crypto.InitiatorHelloMessage.Builder other) {
    if (other == null) {
      return new net.corda.p2p.crypto.InitiatorHelloMessage.Builder();
    } else {
      return new net.corda.p2p.crypto.InitiatorHelloMessage.Builder(other);
    }
  }

  /**
   * Creates a new InitiatorHelloMessage RecordBuilder by copying an existing InitiatorHelloMessage instance.
   * @param other The existing instance to copy.
   * @return A new InitiatorHelloMessage RecordBuilder
   */
  public static net.corda.p2p.crypto.InitiatorHelloMessage.Builder newBuilder(net.corda.p2p.crypto.InitiatorHelloMessage other) {
    if (other == null) {
      return new net.corda.p2p.crypto.InitiatorHelloMessage.Builder();
    } else {
      return new net.corda.p2p.crypto.InitiatorHelloMessage.Builder(other);
    }
  }

  /**
   * RecordBuilder for InitiatorHelloMessage instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<InitiatorHelloMessage>
    implements org.apache.avro.data.RecordBuilder<InitiatorHelloMessage> {

    private net.corda.p2p.crypto.CommonHeader header;
    private net.corda.p2p.crypto.CommonHeader.Builder headerBuilder;
    private java.nio.ByteBuffer initiatorPublicKey;
    private java.util.List<net.corda.p2p.crypto.ProtocolMode> supportedModes;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.crypto.InitiatorHelloMessage.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.header)) {
        this.header = data().deepCopy(fields()[0].schema(), other.header);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasHeaderBuilder()) {
        this.headerBuilder = net.corda.p2p.crypto.CommonHeader.newBuilder(other.getHeaderBuilder());
      }
      if (isValidValue(fields()[1], other.initiatorPublicKey)) {
        this.initiatorPublicKey = data().deepCopy(fields()[1].schema(), other.initiatorPublicKey);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
      if (isValidValue(fields()[2], other.supportedModes)) {
        this.supportedModes = data().deepCopy(fields()[2].schema(), other.supportedModes);
        fieldSetFlags()[2] = other.fieldSetFlags()[2];
      }
    }

    /**
     * Creates a Builder by copying an existing InitiatorHelloMessage instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.crypto.InitiatorHelloMessage other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.header)) {
        this.header = data().deepCopy(fields()[0].schema(), other.header);
        fieldSetFlags()[0] = true;
      }
      this.headerBuilder = null;
      if (isValidValue(fields()[1], other.initiatorPublicKey)) {
        this.initiatorPublicKey = data().deepCopy(fields()[1].schema(), other.initiatorPublicKey);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.supportedModes)) {
        this.supportedModes = data().deepCopy(fields()[2].schema(), other.supportedModes);
        fieldSetFlags()[2] = true;
      }
    }

    /**
      * Gets the value of the 'header' field.
      * @return The value.
      */
    public net.corda.p2p.crypto.CommonHeader getHeader() {
      return header;
    }


    /**
      * Sets the value of the 'header' field.
      * @param value The value of 'header'.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder setHeader(net.corda.p2p.crypto.CommonHeader value) {
      validate(fields()[0], value);
      this.headerBuilder = null;
      this.header = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'header' field has been set.
      * @return True if the 'header' field has been set, false otherwise.
      */
    public boolean hasHeader() {
      return fieldSetFlags()[0];
    }

    /**
     * Gets the Builder instance for the 'header' field and creates one if it doesn't exist yet.
     * @return This builder.
     */
    public net.corda.p2p.crypto.CommonHeader.Builder getHeaderBuilder() {
      if (headerBuilder == null) {
        if (hasHeader()) {
          setHeaderBuilder(net.corda.p2p.crypto.CommonHeader.newBuilder(header));
        } else {
          setHeaderBuilder(net.corda.p2p.crypto.CommonHeader.newBuilder());
        }
      }
      return headerBuilder;
    }

    /**
     * Sets the Builder instance for the 'header' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder setHeaderBuilder(net.corda.p2p.crypto.CommonHeader.Builder value) {
      clearHeader();
      headerBuilder = value;
      return this;
    }

    /**
     * Checks whether the 'header' field has an active Builder instance
     * @return True if the 'header' field has an active Builder instance
     */
    public boolean hasHeaderBuilder() {
      return headerBuilder != null;
    }

    /**
      * Clears the value of the 'header' field.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder clearHeader() {
      header = null;
      headerBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'initiatorPublicKey' field.
      * @return The value.
      */
    public java.nio.ByteBuffer getInitiatorPublicKey() {
      return initiatorPublicKey;
    }


    /**
      * Sets the value of the 'initiatorPublicKey' field.
      * @param value The value of 'initiatorPublicKey'.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder setInitiatorPublicKey(java.nio.ByteBuffer value) {
      validate(fields()[1], value);
      this.initiatorPublicKey = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'initiatorPublicKey' field has been set.
      * @return True if the 'initiatorPublicKey' field has been set, false otherwise.
      */
    public boolean hasInitiatorPublicKey() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'initiatorPublicKey' field.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder clearInitiatorPublicKey() {
      initiatorPublicKey = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /**
      * Gets the value of the 'supportedModes' field.
      * @return The value.
      */
    public java.util.List<net.corda.p2p.crypto.ProtocolMode> getSupportedModes() {
      return supportedModes;
    }


    /**
      * Sets the value of the 'supportedModes' field.
      * @param value The value of 'supportedModes'.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder setSupportedModes(java.util.List<net.corda.p2p.crypto.ProtocolMode> value) {
      validate(fields()[2], value);
      this.supportedModes = value;
      fieldSetFlags()[2] = true;
      return this;
    }

    /**
      * Checks whether the 'supportedModes' field has been set.
      * @return True if the 'supportedModes' field has been set, false otherwise.
      */
    public boolean hasSupportedModes() {
      return fieldSetFlags()[2];
    }


    /**
      * Clears the value of the 'supportedModes' field.
      * @return This builder.
      */
    public net.corda.p2p.crypto.InitiatorHelloMessage.Builder clearSupportedModes() {
      supportedModes = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public InitiatorHelloMessage build() {
      try {
        InitiatorHelloMessage record = new InitiatorHelloMessage();
        if (headerBuilder != null) {
          try {
            record.header = this.headerBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("header"));
            throw e;
          }
        } else {
          record.header = fieldSetFlags()[0] ? this.header : (net.corda.p2p.crypto.CommonHeader) defaultValue(fields()[0]);
        }
        record.initiatorPublicKey = fieldSetFlags()[1] ? this.initiatorPublicKey : (java.nio.ByteBuffer) defaultValue(fields()[1]);
        record.supportedModes = fieldSetFlags()[2] ? this.supportedModes : (java.util.List<net.corda.p2p.crypto.ProtocolMode>) defaultValue(fields()[2]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<InitiatorHelloMessage>
    WRITER$ = (org.apache.avro.io.DatumWriter<InitiatorHelloMessage>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<InitiatorHelloMessage>
    READER$ = (org.apache.avro.io.DatumReader<InitiatorHelloMessage>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.header.customEncode(out);

    out.writeBytes(this.initiatorPublicKey);

    long size0 = this.supportedModes.size();
    out.writeArrayStart();
    out.setItemCount(size0);
    long actualSize0 = 0;
    for (net.corda.p2p.crypto.ProtocolMode e0: this.supportedModes) {
      actualSize0++;
      out.startItem();
      out.writeEnum(e0.ordinal());
    }
    out.writeArrayEnd();
    if (actualSize0 != size0)
      throw new java.util.ConcurrentModificationException("Array-size written was " + size0 + ", but element count was " + actualSize0 + ".");

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.header == null) {
        this.header = new net.corda.p2p.crypto.CommonHeader();
      }
      this.header.customDecode(in);

      this.initiatorPublicKey = in.readBytes(this.initiatorPublicKey);

      long size0 = in.readArrayStart();
      java.util.List<net.corda.p2p.crypto.ProtocolMode> a0 = this.supportedModes;
      if (a0 == null) {
        a0 = new SpecificData.Array<net.corda.p2p.crypto.ProtocolMode>((int)size0, SCHEMA$.getField("supportedModes").schema());
        this.supportedModes = a0;
      } else a0.clear();
      SpecificData.Array<net.corda.p2p.crypto.ProtocolMode> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<net.corda.p2p.crypto.ProtocolMode>)a0 : null);
      for ( ; 0 < size0; size0 = in.arrayNext()) {
        for ( ; size0 != 0; size0--) {
          net.corda.p2p.crypto.ProtocolMode e0 = (ga0 != null ? ga0.peek() : null);
          e0 = net.corda.p2p.crypto.ProtocolMode.values()[in.readEnum()];
          a0.add(e0);
        }
      }

    } else {
      for (int i = 0; i < 3; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.header == null) {
            this.header = new net.corda.p2p.crypto.CommonHeader();
          }
          this.header.customDecode(in);
          break;

        case 1:
          this.initiatorPublicKey = in.readBytes(this.initiatorPublicKey);
          break;

        case 2:
          long size0 = in.readArrayStart();
          java.util.List<net.corda.p2p.crypto.ProtocolMode> a0 = this.supportedModes;
          if (a0 == null) {
            a0 = new SpecificData.Array<net.corda.p2p.crypto.ProtocolMode>((int)size0, SCHEMA$.getField("supportedModes").schema());
            this.supportedModes = a0;
          } else a0.clear();
          SpecificData.Array<net.corda.p2p.crypto.ProtocolMode> ga0 = (a0 instanceof SpecificData.Array ? (SpecificData.Array<net.corda.p2p.crypto.ProtocolMode>)a0 : null);
          for ( ; 0 < size0; size0 = in.arrayNext()) {
            for ( ; size0 != 0; size0--) {
              net.corda.p2p.crypto.ProtocolMode e0 = (ga0 != null ? ga0.peek() : null);
              e0 = net.corda.p2p.crypto.ProtocolMode.values()[in.readEnum()];
              a0.add(e0);
            }
          }
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










