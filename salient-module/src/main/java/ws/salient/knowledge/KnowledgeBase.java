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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.kie.api.KieBase;
import org.kie.api.builder.ReleaseId;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.marshalling.MarshallerFactory;
import org.nustaq.serialization.FSTConfiguration;
import ws.salient.knowledge.SerializableStrategy.Format;
import static ws.salient.knowledge.SerializableStrategy.Format.FST;

public class KnowledgeBase {

    private ReleaseId releaseId;
    private String name;
    private KieBase base;
    private KieContainer container;
    private FSTConfiguration serializer;
    private InternalKieModule module;
    private ObjectMapper json;
    private Marshaller marshaller;
    private SerializableStrategy strategy;

    public KnowledgeBase() {
    }

    public KnowledgeBase(String name) {
        this.name = name;
    }

    public ReleaseId getReleaseId() {
        return releaseId;
    }

    public KieContainer getContainer() {
        return container;
    }

    public FSTConfiguration getSerializer() {
        return serializer;
    }

    public InternalKieModule getModule() {
        return module;
    }

    public ObjectMapper getJson() {
        return json;
    }

    public void setReleaseId(ReleaseId releaseId) {
        this.releaseId = releaseId;
    }

    public void setContainer(KieContainer container) {
        this.container = container;
    }

    public void setSerializer(FSTConfiguration serializer) {
        this.serializer = serializer;
    }

    public void setModule(InternalKieModule module) {
        this.module = module;
    }

    public void setJson(ObjectMapper json) {
        this.json = json;
    }

    public KnowledgeBase withReleaseId(ReleaseId releaseId) {
        this.releaseId = releaseId;
        return this;
    }

    public KnowledgeBase withContainer(KieContainer container) {
        this.container = container;
        return this;
    }

    public KnowledgeBase withSerializer(FSTConfiguration serializer) {
        this.serializer = serializer;
        return this;
    }

    public KnowledgeBase withModule(InternalKieModule module) {
        this.module = module;
        return this;
    }

    public KnowledgeBase withJson(ObjectMapper json) {
        this.json = json;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KieBase getBase() {
        return base;
    }

    public void setBase(KieBase base) {
        this.base = base;
    }

    public KnowledgeBase withBase(KieBase base) {
        this.base = base;
        return this;
    }

    public Marshaller getMarshaller() {
        return getMarshaller(FST);
    }
    
    public Marshaller getMarshaller(Format format) {
        if (strategy == null) {
            strategy = new SerializableStrategy(container.getClassLoader());
        }
        if (marshaller == null) {
            marshaller = MarshallerFactory.newMarshaller(base, new ObjectMarshallingStrategy[]{
                strategy
            });
        }
        strategy.setFormat(format);
        return marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.releaseId);
        hash = 59 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final KnowledgeBase other = (KnowledgeBase) obj;
        if (!Objects.equals(this.releaseId, other.releaseId)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return true;
    }
    

}
