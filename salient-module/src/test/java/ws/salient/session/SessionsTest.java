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

package ws.salient.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import ws.salient.account.Profiles;
import ws.salient.examples.chat.Message;
import ws.salient.knowledge.ClasspathRepository;
import ws.salient.model.Command;
import ws.salient.model.commands.Insert;

public class SessionsTest {

    ObjectMapper json;
    Sessions sessions;
    ThreadPoolExecutor executor;

    @Before
    public void before() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        json = new ObjectMapper();
        sessions = new Sessions(new ClasspathRepository(),
                new Profiles() {},
                new SessionStore() {},
                Guice.createInjector(),
                executor,
                executor);
    }

    @Test
    public void sayHello() throws Exception {

        Command command = new Insert().withObjects(json.createArrayNode().add(json.createObjectNode()
                .putPOJO("ws.salient.examples.chat.Message",
                        new Message("hi", Message.Intent.HELLO))))
                .withAccountId("account")
                .withSessionId("session")
                .withKnowledgeBaseId("ws.salient:salient:1.0.0:ws.salient.examples.chat")
                .withProfile("default");
        sessions.execute(Arrays.asList(command));
        
        finish();
        
        Session session = sessions.getSession(command);
        assertEquals(0, session.getProcessCount());

    }

    private void finish() throws InterruptedException {
        while (executor.getActiveCount() + executor.getQueue().size() > 0) {
            Thread.sleep(1000);
        }
    }

}
