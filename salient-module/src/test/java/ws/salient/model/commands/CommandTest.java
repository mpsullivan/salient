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

package ws.salient.model.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import ws.salient.examples.chat.Message;
import ws.salient.examples.chat.Message.Intent;
import ws.salient.model.Command;

public class CommandTest {

    ObjectMapper json;
    KieServices kie;
    Instant now;

    @Before
    public void before() {
        kie = KieServices.Factory.get();
        json = new ObjectMapper().findAndRegisterModules();
        now = Instant.now();
    }

    @Test
    public void insertCommand() throws Exception {
        
        Command command = new Insert("session")
                .withObjects(json.createArrayNode().add(json.createObjectNode()
                                .putPOJO("ws.salient.examples.chat.Message",
                                        new Message("hi", Intent.HELLO))))
                .withAccountId("account")
                .withKnowledgeBaseId("ws.salient:salient:1.0.0:ws.salient.examples.chat")
                .withProfile("default")
                .withTimestamp(now);

        String jsonCommand = json.writeValueAsString(command);
        command = json.readValue(jsonCommand, Command.class);
        
        assertTrue(command instanceof Insert);
        Insert insert = (Insert) command;
        
        assertEquals("session", insert.getSessionId());
        assertEquals("account", insert.getAccountId());
        assertEquals("ws.salient:salient:1.0.0:ws.salient.examples.chat",
                insert.getKnowledgeBaseId());
        assertEquals(1, insert.getProfiles().size());
        assertEquals("default", insert.getProfiles().get(0));
        assertEquals(now, command.getTimestamp());
        assertNotNull(insert.getObjects());
        assertEquals(1, insert.getObjects().size());

    }
    
    @Test
    public void completeWorkItemCommand() throws Exception {
        
        Command command = new CompleteWorkItem(1L, "session")
                .withResult(Collections.singletonMap("success", Boolean.TRUE))
                .withTimestamp(now);
        
        String jsonCommand = json.writeValueAsString(command);
        command = json.readValue(jsonCommand, Command.class);
        
        assertTrue(command instanceof CompleteWorkItem);
        CompleteWorkItem completeWorkItem = (CompleteWorkItem) command;
        
        assertEquals("session", completeWorkItem.getSessionId());
        assertEquals(now, command.getTimestamp());
        assertEquals(new Long(1), completeWorkItem.getWorkItemId());
        assertNotNull(completeWorkItem.getResult());
        assertTrue(completeWorkItem.getResult().containsKey("success"));
        assertTrue((Boolean)completeWorkItem.getResult().get("success"));
        
    }
    
    @Test
    public void abortWorkItemCommand() throws Exception {
        
        Command command = new AbortWorkItem(1L, "session")
                .withTimestamp(now);
        
        String jsonCommand = json.writeValueAsString(command);
        command = json.readValue(jsonCommand, Command.class);
        
        assertTrue(command instanceof AbortWorkItem);
        AbortWorkItem abortWorkItem = (AbortWorkItem) command;
        assertEquals(now, command.getTimestamp());
        assertEquals("session", abortWorkItem.getSessionId());
        assertEquals(new Long(1), abortWorkItem.getWorkItemId());
        
    }
    
    @Test
    public void workItemExceptionCommand() throws Exception {
        
        Command command = new WorkItemException(1L, "session")
                .withException(new RuntimeException("Error"))
                .withTimestamp(now);
        
        String jsonCommand = json.writeValueAsString(command);
        command = json.readValue(jsonCommand, Command.class);
        
        assertTrue(command instanceof WorkItemException);
        WorkItemException workItemException = (WorkItemException) command;
        
        assertEquals("session", workItemException.getSessionId());
        assertEquals(new Long(1), workItemException.getWorkItemId());
        assertNotNull(workItemException.getException());
        assertEquals(now, command.getTimestamp());
        assertTrue(workItemException.getException() instanceof RuntimeException);
        
    }
    
}
