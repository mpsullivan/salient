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
import java.util.Locale;
import static junit.framework.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.internal.marshalling.MarshallerFactory;
import static org.mockito.Mockito.mock;
import ws.salient.examples.chat.Chat;
import ws.salient.examples.chat.Message;
import ws.salient.examples.chat.Message.Intent;
import ws.salient.knowledge.SerializableStrategy.Format;
import static ws.salient.knowledge.SerializableStrategy.Format.FST;
import static ws.salient.knowledge.SerializableStrategy.Format.JAVA;


public class SerializableStrategyTest {
    
    KieContainer kcontainer;
    KieBase kbase;
    KieSession ksession;
    WorkItemHandler functionHandler;
    
    @Before
    public void before() {
        kcontainer = KieServices.Factory.get().getKieClasspathContainer();
        kbase = kcontainer.getKieBase("ws.salient.examples.chat");
       
        ksession = kbase.newKieSession();
        ksession.setGlobal("chat", new Chat(ksession, Locale.US, kcontainer.getClassLoader()));
        
        functionHandler = mock(WorkItemHandler.class);
        ksession.getWorkItemManager().registerWorkItemHandler("ws.salient.examples.chat.FunctionHandler", functionHandler);
    }
    
    @Test
    public void javaSerialize() throws Exception {
        serialize(JAVA, 876);
    }
    
    @Test
    public void fstSerialize() throws Exception {
        serialize(FST, 665);
    }

    public void serialize(Format format, int expectedSize) throws Exception {
        
        Marshaller marshaller = MarshallerFactory.newMarshaller(kbase, new ObjectMarshallingStrategy[] {
                new SerializableStrategy(kcontainer.getClassLoader(), format)
            });
         
       ksession.insert(new Message("hi", Intent.HELLO));
       ksession.fireAllRules();
       
       ByteArrayOutputStream sessionOut = new ByteArrayOutputStream();
       marshaller.marshall(sessionOut, ksession);
       
       
       byte[] sessionBytes = sessionOut.toByteArray();

       assertEquals(expectedSize, sessionBytes.length);
       
       ksession = kbase.newKieSession();
       marshaller.unmarshall(new ByteArrayInputStream(sessionBytes), ksession);
       
       assertEquals(1, ksession.getFactCount());
       assertEquals(1, ksession.getProcessInstances().size());

    }
    
    
}
