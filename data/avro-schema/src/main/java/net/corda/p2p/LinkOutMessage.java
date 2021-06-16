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
public class LinkOutMessage extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -8983819736993591898L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"LinkOutMessage\",\"namespace\":\"net.corda.p2p\",\"fields\":[{\"name\":\"header\",\"type\":{\"type\":\"record\",\"name\":\"LinkOutHeader\",\"fields\":[{\"name\":\"sni\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"address\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"payload\",\"type\":[{\"type\":\"record\",\"name\":\"AuthenticatedDataMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":{\"type\":\"record\",\"name\":\"CommonHeader\",\"fields\":[{\"name\":\"messageType\",\"type\":{\"type\":\"enum\",\"name\":\"MessageType\",\"symbols\":[\"INITIATOR_HELLO\",\"RESPONDER_HELLO\",\"INITIATOR_HANDSHAKE\",\"RESPONDER_HANDSHAKE\",\"DATA\"]}},{\"name\":\"protocolVersion\",\"type\":\"int\"},{\"name\":\"sessionId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"sequenceNo\",\"type\":\"long\"},{\"name\":\"timestamp\",\"type\":\"long\"}]}},{\"name\":\"payload\",\"type\":\"bytes\"},{\"name\":\"authTag\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"AuthenticatedEncryptedDataMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"encryptedPayload\",\"type\":\"bytes\"},{\"name\":\"authTag\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"InitiatorHelloMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"initiatorPublicKey\",\"type\":\"bytes\"},{\"name\":\"supportedModes\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"enum\",\"name\":\"ProtocolMode\",\"symbols\":[\"AUTHENTICATION_ONLY\",\"AUTHENTICATED_ENCRYPTION\"]}}}]},{\"type\":\"record\",\"name\":\"InitiatorHandshakeMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"encryptedData\",\"type\":\"bytes\"},{\"name\":\"authTag\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"ResponderHelloMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"responderPublicKey\",\"type\":\"bytes\"},{\"name\":\"selectedMode\",\"type\":\"ProtocolMode\"}]},{\"type\":\"record\",\"name\":\"ResponderHandshakeMessage\",\"namespace\":\"net.corda.p2p.crypto\",\"fields\":[{\"name\":\"header\",\"type\":\"CommonHeader\"},{\"name\":\"encryptedData\",\"type\":\"bytes\"},{\"name\":\"authTag\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"Step2Message\",\"fields\":[{\"name\":\"initiatorHello\",\"type\":\"net.corda.p2p.crypto.InitiatorHelloMessage\"},{\"name\":\"responderHello\",\"type\":\"net.corda.p2p.crypto.ResponderHelloMessage\"},{\"name\":\"privateKey\",\"type\":\"bytes\"}]}]}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<LinkOutMessage> ENCODER =
      new BinaryMessageEncoder<LinkOutMessage>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<LinkOutMessage> DECODER =
      new BinaryMessageDecoder<LinkOutMessage>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<LinkOutMessage> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<LinkOutMessage> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<LinkOutMessage> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<LinkOutMessage>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this LinkOutMessage to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a LinkOutMessage from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a LinkOutMessage instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static LinkOutMessage fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.p2p.LinkOutHeader header;
   private java.lang.Object payload;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public LinkOutMessage() {}

  /**
   * All-args constructor.
   * @param header The new value for header
   * @param payload The new value for payload
   */
  public LinkOutMessage(net.corda.p2p.LinkOutHeader header, java.lang.Object payload) {
    this.header = header;
    this.payload = payload;
  }

  public org.apache.avro.specific.SpecificData getSpecificData() { return MODEL$; }
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return header;
    case 1: return payload;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: header = (net.corda.p2p.LinkOutHeader)value$; break;
    case 1: payload = value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'header' field.
   * @return The value of the 'header' field.
   */
  public net.corda.p2p.LinkOutHeader getHeader() {
    return header;
  }


  /**
   * Sets the value of the 'header' field.
   * @param value the value to set.
   */
  public void setHeader(net.corda.p2p.LinkOutHeader value) {
    this.header = value;
  }

  /**
   * Gets the value of the 'payload' field.
   * @return The value of the 'payload' field.
   */
  public java.lang.Object getPayload() {
    return payload;
  }


  /**
   * Sets the value of the 'payload' field.
   * @param value the value to set.
   */
  public void setPayload(java.lang.Object value) {
    this.payload = value;
  }

  /**
   * Creates a new LinkOutMessage RecordBuilder.
   * @return A new LinkOutMessage RecordBuilder
   */
  public static net.corda.p2p.LinkOutMessage.Builder newBuilder() {
    return new net.corda.p2p.LinkOutMessage.Builder();
  }

  /**
   * Creates a new LinkOutMessage RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new LinkOutMessage RecordBuilder
   */
  public static net.corda.p2p.LinkOutMessage.Builder newBuilder(net.corda.p2p.LinkOutMessage.Builder other) {
    if (other == null) {
      return new net.corda.p2p.LinkOutMessage.Builder();
    } else {
      return new net.corda.p2p.LinkOutMessage.Builder(other);
    }
  }

  /**
   * Creates a new LinkOutMessage RecordBuilder by copying an existing LinkOutMessage instance.
   * @param other The existing instance to copy.
   * @return A new LinkOutMessage RecordBuilder
   */
  public static net.corda.p2p.LinkOutMessage.Builder newBuilder(net.corda.p2p.LinkOutMessage other) {
    if (other == null) {
      return new net.corda.p2p.LinkOutMessage.Builder();
    } else {
      return new net.corda.p2p.LinkOutMessage.Builder(other);
    }
  }

  /**
   * RecordBuilder for LinkOutMessage instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<LinkOutMessage>
    implements org.apache.avro.data.RecordBuilder<LinkOutMessage> {

    private net.corda.p2p.LinkOutHeader header;
    private net.corda.p2p.LinkOutHeader.Builder headerBuilder;
    private java.lang.Object payload;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.LinkOutMessage.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.header)) {
        this.header = data().deepCopy(fields()[0].schema(), other.header);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasHeaderBuilder()) {
        this.headerBuilder = net.corda.p2p.LinkOutHeader.newBuilder(other.getHeaderBuilder());
      }
      if (isValidValue(fields()[1], other.payload)) {
        this.payload = data().deepCopy(fields()[1].schema(), other.payload);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing LinkOutMessage instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.LinkOutMessage other) {
      super(SCHEMA$);
      if (isValidValue(fields()[0], other.header)) {
        this.header = data().deepCopy(fields()[0].schema(), other.header);
        fieldSetFlags()[0] = true;
      }
      this.headerBuilder = null;
      if (isValidValue(fields()[1], other.payload)) {
        this.payload = data().deepCopy(fields()[1].schema(), other.payload);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'header' field.
      * @return The value.
      */
    public net.corda.p2p.LinkOutHeader getHeader() {
      return header;
    }


    /**
      * Sets the value of the 'header' field.
      * @param value The value of 'header'.
      * @return This builder.
      */
    public net.corda.p2p.LinkOutMessage.Builder setHeader(net.corda.p2p.LinkOutHeader value) {
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
    public net.corda.p2p.LinkOutHeader.Builder getHeaderBuilder() {
      if (headerBuilder == null) {
        if (hasHeader()) {
          setHeaderBuilder(net.corda.p2p.LinkOutHeader.newBuilder(header));
        } else {
          setHeaderBuilder(net.corda.p2p.LinkOutHeader.newBuilder());
        }
      }
      return headerBuilder;
    }

    /**
     * Sets the Builder instance for the 'header' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.LinkOutMessage.Builder setHeaderBuilder(net.corda.p2p.LinkOutHeader.Builder value) {
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
    public net.corda.p2p.LinkOutMessage.Builder clearHeader() {
      header = null;
      headerBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'payload' field.
      * @return The value.
      */
    public java.lang.Object getPayload() {
      return payload;
    }


    /**
      * Sets the value of the 'payload' field.
      * @param value The value of 'payload'.
      * @return This builder.
      */
    public net.corda.p2p.LinkOutMessage.Builder setPayload(java.lang.Object value) {
      validate(fields()[1], value);
      this.payload = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'payload' field has been set.
      * @return True if the 'payload' field has been set, false otherwise.
      */
    public boolean hasPayload() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'payload' field.
      * @return This builder.
      */
    public net.corda.p2p.LinkOutMessage.Builder clearPayload() {
      payload = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LinkOutMessage build() {
      try {
        LinkOutMessage record = new LinkOutMessage();
        if (headerBuilder != null) {
          try {
            record.header = this.headerBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("header"));
            throw e;
          }
        } else {
          record.header = fieldSetFlags()[0] ? this.header : (net.corda.p2p.LinkOutHeader) defaultValue(fields()[0]);
        }
        record.payload = fieldSetFlags()[1] ? this.payload :  defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<LinkOutMessage>
    WRITER$ = (org.apache.avro.io.DatumWriter<LinkOutMessage>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<LinkOutMessage>
    READER$ = (org.apache.avro.io.DatumReader<LinkOutMessage>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}










