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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ws.salient.model.Command;
import ws.salient.model.commands.WorkItemException;

public class AsyncTaskHandler implements WorkItemHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskHandler.class);

    private final WorkItemHandler handler;
    private final String sessionId;
    private final Sessions sessions;
    private final List<Long> completedWorkItemIds = new LinkedList();

    public AsyncTaskHandler(String sessionId, WorkItemHandler handler, Sessions sessions) {
        this.handler = handler;
        this.sessionId = sessionId;
        this.sessions = sessions;
    }

    public List<Long> getCompletedWorkItemIds() {
        return completedWorkItemIds;
    }

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        if (completedWorkItemIds.contains(workItem.getId())) {
            log.info("Work item completed: " + workItem.getId());
        } else {
            CompletableFuture.runAsync(() -> {
                MDC.put("sessionId", sessionId);
                try {
                    handler.executeWorkItem(workItem, new AsyncTaskManager(sessions, sessionId));
                } catch (RuntimeException ex) {
                    Command command = new WorkItemException(workItem.getId(), sessionId)
                            .withException(ex);
                    command.setTimestamp(Instant.now());
                    sessions.execute(Collections.singletonList(command));
                }
            }, sessions.workItemExecutor);
        }
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {

    }

}
