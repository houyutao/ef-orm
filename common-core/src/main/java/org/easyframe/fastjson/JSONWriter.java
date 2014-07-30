package org.easyframe.fastjson;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;

import static org.easyframe.fastjson.JSONStreamContext.*;
import org.easyframe.fastjson.serializer.JSONSerializer;
import org.easyframe.fastjson.serializer.SerializeWriter;
import org.easyframe.fastjson.serializer.SerializerFeature;

public class JSONWriter implements Closeable, Flushable {

    private SerializeWriter   writer;

    private JSONSerializer    serializer;

    private JSONStreamContext context;

    public JSONWriter(Writer out){
        writer = new SerializeWriter(out);
        serializer = new JSONSerializer(writer);
    }

    public void config(SerializerFeature feature, boolean state) {
        this.writer.config(feature, state);
    }

    public void startObject() {
        if (context != null) {
            beginStructure();
        }
        context = new JSONStreamContext(context, JSONStreamContext.StartObject);
        writer.write('{');
    }

    public void endObject() {
        writer.write('}');
        endStructure();
    }

    public void writeKey(String key) {
        writeObject(key);
    }

    public void writeValue(Object object) {
        writeObject(object);
    }

    public void writeObject(String object) {
        beforeWrite();

        serializer.write(object);

        afterWriter();
    }

    public void writeObject(Object object) {
        beforeWrite();
        serializer.write(object);
        afterWriter();
    }

    public void startArray() {
        if (context != null) {
            beginStructure();
        }

        context = new JSONStreamContext(context, StartArray);
        writer.write('[');
    }

    private void beginStructure() {
        final int state = context.getState();
        switch (state) {
            case PropertyKey:
                writer.write(':');
                break;
            case ArrayValue:
                writer.write(',');
                break;
            case StartObject:
                break;
            case StartArray:
                break;
            default:
                throw new JSONException("illegal state : " + state);
        }
    }

    public void endArray() {
        writer.write(']');
        endStructure();
    }

    private void endStructure() {
        context = context.getParent();

        if (context == null) {
            return;
        }
        
        final int state = context.getState();
        int newState = -1;
        switch (state) {
            case PropertyKey:
                newState = PropertyValue;
                break;
            case StartArray:
                newState = ArrayValue;
                break;
            case ArrayValue:
                break;
            case StartObject:
                newState = PropertyKey;
                break;
            default:
                break;
        }
        if (newState != -1) {
            context.setState(newState);
        }
    }

    private void beforeWrite() {
        if (context == null) {
            return;
        }
        
        switch (context.getState()) {
            case StartObject:
            case StartArray:
                break;
            case PropertyKey:
                writer.write(':');
                break;
            case PropertyValue:
                writer.write(',');
                break;
            case ArrayValue:
                writer.write(',');
                break;
            default:
                break;
        }
    }

    private void afterWriter() {
        if (context == null) {
            return;
        }

        final int state = context.getState();
        int newState = -1;
        switch (state) {
            case PropertyKey:
                newState = PropertyValue;
                break;
            case StartObject:
            case PropertyValue:
                newState = PropertyKey;
                break;
            case StartArray:
                newState = ArrayValue;
                break;
            case ArrayValue:
                break;
            default:
                break;
        }

        if (newState != -1) {
            context.setState(newState);
        }
    }

    public void flush() throws IOException {
        writer.flush();
    }

    public void close() throws IOException {
        writer.close();
    }

    @Deprecated
    public void writeStartObject() {
        startObject();
    }

    @Deprecated
    public void writeEndObject() {
        endObject();
    }

    @Deprecated
    public void writeStartArray() {
        startArray();
    }

    @Deprecated
    public void writeEndArray() {
        endArray();
    }
}
