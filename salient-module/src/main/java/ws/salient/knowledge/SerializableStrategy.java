/*
 * Copyright 2016 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ws.salient.knowledge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.List;
import org.drools.core.marshalling.impl.PersisterHelper;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.marshalling.ObjectMarshallingStrategy.Context;
import org.nustaq.serialization.FSTConfiguration;
import static ws.salient.knowledge.SerializableStrategy.Format.FST;
import static ws.salient.knowledge.SerializableStrategy.Format.JAVA;

public class SerializableStrategy implements ObjectMarshallingStrategy {

    public static enum Format {
        JAVA, FST
    };
    
    private final ClassLoader classLoader;
    private FSTConfiguration fst;
    private Format format;

    public SerializableStrategy(ClassLoader classLoader, Format format) {
        this.classLoader = classLoader;
        this.format = format;
        fst = FSTConfiguration.createDefaultConfiguration();
        fst.setShareReferences(true);
        fst.setForceSerializable(false);
        fst.setClassLoader(classLoader);
    }
    
    public SerializableStrategy(ClassLoader classLoader) {
        this(classLoader, FST);
    }

    public void setFormat(Format format) {
        this.format = format;
    }
    
    public SerializableStrategy withFSTConfiguration(FSTConfiguration fst) {
        this.fst = fst;
        return this;
    }

    public Object read(ObjectInputStream objectIn) throws IOException,
            ClassNotFoundException {
        return null;
    }

    public void write(ObjectOutputStream objectOut,
            Object object) throws IOException {
    }

    public boolean accept(Object object) {
        return true;
    }

    public byte[] marshal(Context context,
            ObjectOutputStream os,
            Object object) throws IOException {
        SerializableContext ctx = (SerializableContext) context;
        int index = ctx.data.size();
        ctx.data.add(object);
        return PersisterHelper.intToByteArray(index);
    }

    public Object unmarshal(Context context,
            ObjectInputStream is,
            byte[] object,
            ClassLoader classloader) throws IOException, ClassNotFoundException {
        SerializableContext ctx = (SerializableContext) context;
        return ctx.data.get(PersisterHelper.byteArrayToInt(object));
    }

    public Context createContext() {
        return new SerializableContext(classLoader, fst, format);
    }

    protected static class SerializableContext implements Context {

        private final ClassLoader classLoader;
        private final FSTConfiguration fst;
        private final Format format;

        public SerializableContext(ClassLoader classLoader, FSTConfiguration fst, Format format) {
            this.classLoader = classLoader;
            this.fst = fst;
            this.format = format;
        }

        public List<Object> data = new ArrayList<>();

        public void read(ObjectInputStream in) throws IOException,
                ClassNotFoundException {
            Format format = Format.valueOf(in.readUTF());
            int length = in.readInt();
            byte[] dataBytes = new byte[length];
            in.readFully(dataBytes);
                
            if (format.equals(JAVA)) {
                ObjectInputStream objectIn = new ObjectInputStream(new ByteArrayInputStream(dataBytes)) {
                    protected Class<?> resolveClass(ObjectStreamClass desc)
                            throws IOException,
                            ClassNotFoundException {
                        return classLoader.loadClass(desc.getName());
                    }
                };
                this.data = (List<Object>) objectIn.readObject();
            } else {
                this.data = (List<Object>) fst.asObject(dataBytes);
            }

        }

        public void write(ObjectOutputStream out) throws IOException {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            byte[] bytes;
            if (format.equals(FST)) {
                bytes = fst.asByteArray(data);
            } else {
                try (ObjectOutputStream dataOut = new ObjectOutputStream(bytesOut)) {
                    dataOut.writeObject(data);
                    dataOut.flush();
                }
                bytes = bytesOut.toByteArray();
            }
            out.writeUTF(format.name());
            out.writeInt(bytes.length);
            out.write(bytes);
            out.flush();
        }
    }

}
