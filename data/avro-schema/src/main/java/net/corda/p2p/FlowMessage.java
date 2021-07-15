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
public class FlowMessage extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = -3496030710159948003L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"FlowMessage\",\"namespace\":\"net.corda.p2p\",\"fields\":[{\"name\":\"header\",\"type\":{\"type\":\"record\",\"name\":\"FlowMessageHeader\",\"fields\":[{\"name\":\"destination\",\"type\":{\"type\":\"record\",\"name\":\"HoldingIdentity\",\"fields\":[{\"name\":\"x500Name\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"groupId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"source\",\"type\":\"HoldingIdentity\"},{\"name\":\"ttl\",\"type\":[\"null\",\"long\"]},{\"name\":\"messageId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}},{\"name\":\"traceId\",\"type\":{\"type\":\"string\",\"avro.java.string\":\"String\"}}]}},{\"name\":\"payload\",\"type\":\"bytes\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<FlowMessage> ENCODER =
      new BinaryMessageEncoder<FlowMessage>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<FlowMessage> DECODER =
      new BinaryMessageDecoder<FlowMessage>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageEncoder instance used by this class.
   * @return the message encoder used by this class
   */
  public static BinaryMessageEncoder<FlowMessage> getEncoder() {
    return ENCODER;
  }

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   * @return the message decoder used by this class
   */
  public static BinaryMessageDecoder<FlowMessage> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   * @return a BinaryMessageDecoder instance for this class backed by the given SchemaStore
   */
  public static BinaryMessageDecoder<FlowMessage> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<FlowMessage>(MODEL$, SCHEMA$, resolver);
  }

  /**
   * Serializes this FlowMessage to a ByteBuffer.
   * @return a buffer holding the serialized data for this instance
   * @throws java.io.IOException if this instance could not be serialized
   */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /**
   * Deserializes a FlowMessage from a ByteBuffer.
   * @param b a byte buffer holding serialized data for an instance of this class
   * @return a FlowMessage instance decoded from the given buffer
   * @throws java.io.IOException if the given bytes could not be deserialized into an instance of this class
   */
  public static FlowMessage fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

   private net.corda.p2p.FlowMessageHeader header;
   private java.nio.ByteBuffer payload;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public FlowMessage() {}

  /**
   * All-args constructor.
   * @param header The new value for header
   * @param payload The new value for payload
   */
  public FlowMessage(net.corda.p2p.FlowMessageHeader header, java.nio.ByteBuffer payload) {
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
    case 0: header = (net.corda.p2p.FlowMessageHeader)value$; break;
    case 1: payload = (java.nio.ByteBuffer)value$; break;
    default: throw new IndexOutOfBoundsException("Invalid index: " + field$);
    }
  }

  /**
   * Gets the value of the 'header' field.
   * @return The value of the 'header' field.
   */
  public net.corda.p2p.FlowMessageHeader getHeader() {
    return header;
  }


  /**
   * Sets the value of the 'header' field.
   * @param value the value to set.
   */
  public void setHeader(net.corda.p2p.FlowMessageHeader value) {
    this.header = value;
  }

  /**
   * Gets the value of the 'payload' field.
   * @return The value of the 'payload' field.
   */
  public java.nio.ByteBuffer getPayload() {
    return payload;
  }


  /**
   * Sets the value of the 'payload' field.
   * @param value the value to set.
   */
  public void setPayload(java.nio.ByteBuffer value) {
    this.payload = value;
  }

  /**
   * Creates a new FlowMessage RecordBuilder.
   * @return A new FlowMessage RecordBuilder
   */
  public static net.corda.p2p.FlowMessage.Builder newBuilder() {
    return new net.corda.p2p.FlowMessage.Builder();
  }

  /**
   * Creates a new FlowMessage RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new FlowMessage RecordBuilder
   */
  public static net.corda.p2p.FlowMessage.Builder newBuilder(net.corda.p2p.FlowMessage.Builder other) {
    if (other == null) {
      return new net.corda.p2p.FlowMessage.Builder();
    } else {
      return new net.corda.p2p.FlowMessage.Builder(other);
    }
  }

  /**
   * Creates a new FlowMessage RecordBuilder by copying an existing FlowMessage instance.
   * @param other The existing instance to copy.
   * @return A new FlowMessage RecordBuilder
   */
  public static net.corda.p2p.FlowMessage.Builder newBuilder(net.corda.p2p.FlowMessage other) {
    if (other == null) {
      return new net.corda.p2p.FlowMessage.Builder();
    } else {
      return new net.corda.p2p.FlowMessage.Builder(other);
    }
  }

  /**
   * RecordBuilder for FlowMessage instances.
   */
  @org.apache.avro.specific.AvroGenerated
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<FlowMessage>
    implements org.apache.avro.data.RecordBuilder<FlowMessage> {

    private net.corda.p2p.FlowMessageHeader header;
    private net.corda.p2p.FlowMessageHeader.Builder headerBuilder;
    private java.nio.ByteBuffer payload;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(net.corda.p2p.FlowMessage.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.header)) {
        this.header = data().deepCopy(fields()[0].schema(), other.header);
        fieldSetFlags()[0] = other.fieldSetFlags()[0];
      }
      if (other.hasHeaderBuilder()) {
        this.headerBuilder = net.corda.p2p.FlowMessageHeader.newBuilder(other.getHeaderBuilder());
      }
      if (isValidValue(fields()[1], other.payload)) {
        this.payload = data().deepCopy(fields()[1].schema(), other.payload);
        fieldSetFlags()[1] = other.fieldSetFlags()[1];
      }
    }

    /**
     * Creates a Builder by copying an existing FlowMessage instance
     * @param other The existing instance to copy.
     */
    private Builder(net.corda.p2p.FlowMessage other) {
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
    public net.corda.p2p.FlowMessageHeader getHeader() {
      return header;
    }


    /**
      * Sets the value of the 'header' field.
      * @param value The value of 'header'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessage.Builder setHeader(net.corda.p2p.FlowMessageHeader value) {
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
    public net.corda.p2p.FlowMessageHeader.Builder getHeaderBuilder() {
      if (headerBuilder == null) {
        if (hasHeader()) {
          setHeaderBuilder(net.corda.p2p.FlowMessageHeader.newBuilder(header));
        } else {
          setHeaderBuilder(net.corda.p2p.FlowMessageHeader.newBuilder());
        }
      }
      return headerBuilder;
    }

    /**
     * Sets the Builder instance for the 'header' field
     * @param value The builder instance that must be set.
     * @return This builder.
     */

    public net.corda.p2p.FlowMessage.Builder setHeaderBuilder(net.corda.p2p.FlowMessageHeader.Builder value) {
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
    public net.corda.p2p.FlowMessage.Builder clearHeader() {
      header = null;
      headerBuilder = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'payload' field.
      * @return The value.
      */
    public java.nio.ByteBuffer getPayload() {
      return payload;
    }


    /**
      * Sets the value of the 'payload' field.
      * @param value The value of 'payload'.
      * @return This builder.
      */
    public net.corda.p2p.FlowMessage.Builder setPayload(java.nio.ByteBuffer value) {
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
    public net.corda.p2p.FlowMessage.Builder clearPayload() {
      payload = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public FlowMessage build() {
      try {
        FlowMessage record = new FlowMessage();
        if (headerBuilder != null) {
          try {
            record.header = this.headerBuilder.build();
          } catch (org.apache.avro.AvroMissingFieldException e) {
            e.addParentField(record.getSchema().getField("header"));
            throw e;
          }
        } else {
          record.header = fieldSetFlags()[0] ? this.header : (net.corda.p2p.FlowMessageHeader) defaultValue(fields()[0]);
        }
        record.payload = fieldSetFlags()[1] ? this.payload : (java.nio.ByteBuffer) defaultValue(fields()[1]);
        return record;
      } catch (org.apache.avro.AvroMissingFieldException e) {
        throw e;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<FlowMessage>
    WRITER$ = (org.apache.avro.io.DatumWriter<FlowMessage>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<FlowMessage>
    READER$ = (org.apache.avro.io.DatumReader<FlowMessage>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

  @Override protected boolean hasCustomCoders() { return true; }

  @Override public void customEncode(org.apache.avro.io.Encoder out)
    throws java.io.IOException
  {
    this.header.customEncode(out);

    out.writeBytes(this.payload);

  }

  @Override public void customDecode(org.apache.avro.io.ResolvingDecoder in)
    throws java.io.IOException
  {
    org.apache.avro.Schema.Field[] fieldOrder = in.readFieldOrderIfDiff();
    if (fieldOrder == null) {
      if (this.header == null) {
        this.header = new net.corda.p2p.FlowMessageHeader();
      }
      this.header.customDecode(in);

      this.payload = in.readBytes(this.payload);

    } else {
      for (int i = 0; i < 2; i++) {
        switch (fieldOrder[i].pos()) {
        case 0:
          if (this.header == null) {
            this.header = new net.corda.p2p.FlowMessageHeader();
          }
          this.header.customDecode(in);
          break;

        case 1:
          this.payload = in.readBytes(this.payload);
          break;

        default:
          throw new java.io.IOException("Corrupt ResolvingDecoder.");
        }
      }
    }
  }
}










