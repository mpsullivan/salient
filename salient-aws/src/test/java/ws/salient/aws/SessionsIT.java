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

package ws.salient.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import ws.salient.aws.databind.AmazonModule;
import ws.salient.aws.dynamodb.DynamoDBProfiles;
import ws.salient.aws.dynamodb.DynamoDBStore;
import ws.salient.aws.s3.AmazonS3Repository;
import ws.salient.model.Command;
import ws.salient.model.commands.Insert;
import ws.salient.session.Session;
import ws.salient.session.Sessions;

public class SessionsIT {

    ObjectMapper json;
    Sessions sessions;
    ThreadPoolExecutor executor;
    String accountId;
    String sessionId;
    DynamoDBStore store;
    AmazonClientProvider provider;

    @Before
    public void before() {
        accountId = "test";
        sessionId = UUID.randomUUID().toString();
        System.setProperty("kie.maven.settings.custom", "src/test/resources/settings.xml");
        Application.unpackRepository("/maven.zip");
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        json = new ObjectMapper().findAndRegisterModules().registerModules(new AmazonModule());

        provider = new AmazonClientProvider();
        store = new DynamoDBStore(provider.getDynamoDB(), provider.getAWSKMS(), json, executor);
        sessions = new Sessions(new AmazonS3Repository(provider.getAmazonS3()),
                new DynamoDBProfiles(provider.getDynamoDB(), provider.getAWSKMS(), json),
                store, Guice.createInjector(provider),
                executor,
                executor);
    }

    @Test
    public void sayHello() throws Exception {

        Command command = insertResponse("Hello");
        
        sessions.execute(Arrays.asList(command)).exceptionally((ex) -> {
            System.out.println(ex);
            return null;
        });

        finish();

        Session session = sessions.getSession(command);
        assertEquals(0, session.getProcessCount());
        
    }

    private void finish() throws InterruptedException {
        while (executor.getActiveCount() + executor.getQueue().size() > 0) {
            Thread.sleep(1000);
        }
    }

    private Command insertResponse(String text) {
        return new Insert()
                .withObjects(json.createArrayNode().add(json.createObjectNode().set("ws.salient.chat.Response", json.createObjectNode().put("text", text))
                        ))
                .withAccountId(accountId)
                .withSessionId(sessionId)
                .withKnowledgeBaseId("ws.salient:chat-kmod:0.2.0:ws.salient.chat");
    } 


}
