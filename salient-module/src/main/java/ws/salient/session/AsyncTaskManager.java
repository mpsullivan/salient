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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import ws.salient.model.Command;
import ws.salient.model.commands.CompleteWorkItem;

public class AsyncTaskManager implements WorkItemManager {

    private final Sessions sessions;
    private final String sessionId;

    public AsyncTaskManager(Sessions sessions, String sessionId) {
        this.sessions = sessions;
        this.sessionId = sessionId;
    }
    
    @Override
    public void completeWorkItem(long workItemId, Map<String, Object> result) {
        completeWorkItemAsync(workItemId, result);
    }
    
    public CompletableFuture completeWorkItemAsync(long workItemId, Map<String, Object> result) {
        Command command = new CompleteWorkItem(workItemId, sessionId).withResult(result);
        command.setTimestamp(Instant.now());
        return sessions.execute(Collections.singletonList(command));
    }

    @Override
    public void abortWorkItem(long l) {
        
    }

    @Override
    public void registerWorkItemHandler(String string, WorkItemHandler wih) {
        
    }
    
    
    
}
