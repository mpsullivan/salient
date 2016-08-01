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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.kie.api.KieServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.salient.account.Profiles;
import ws.salient.knowledge.KnowledgeBase;
import ws.salient.knowledge.KnowledgeRepository;
import ws.salient.model.Command;
import ws.salient.model.commands.ModifyProfile;

public class Sessions {

    private final static Logger log = LoggerFactory.getLogger(Sessions.class);

    // Singletons
    private final SessionStore store;
    private final ObjectMapper json;
    private final KnowledgeRepository repository;

    // Settings
    private final Profiles profiles;

    // Caches
    private final ConcurrentMap<String, Session> sessions;
    
    private final Injector injector;

    // Executors
    protected final ExecutorService commandExecutor;
    protected final ExecutorService workItemExecutor;

    public Sessions(KnowledgeRepository repository, Profiles profiles, SessionStore store, Injector injector, ExecutorService commandExecutor, ExecutorService workItemExecutor) {
        
        this.injector = injector;
       
        this.store = store;

        // Set executors
        this.commandExecutor = commandExecutor;
        this.workItemExecutor = workItemExecutor;

        // Initialise singletons
        json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Load configuration
        this.profiles = profiles;

        this.repository = repository;
        repository.setRemoteRepositories(profiles.getRemoteRepositories());

        // Initialise caches
        sessions = new ConcurrentHashMap();

        // Persist queued items on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });

    }

    public CompletableFuture execute(List<Command> commands) {
        Instant now = Instant.now();
        CompletableFuture result;
        try {
            commands.stream()
                    .filter(command -> command instanceof ModifyProfile)
                    .forEach((command) -> {
                        log.info(toJson(command));
                        profiles.modified(command.getAccountId());
                    });

            commands.stream()
                    .filter(command -> command.getKnowledgeBaseId() != null)
                    .forEach((command) -> {
                        String knowledgeBaseId = command.getKnowledgeBaseId();
                        Map<String, String> aliases = profiles.getAliases(command.getAccountId(), command.getProfiles());
                        if (aliases.containsKey(knowledgeBaseId)) {
                            knowledgeBaseId = aliases.get(knowledgeBaseId);
                        }
                        command.setKnowledgeBaseId(knowledgeBaseId);
                    });

            commands.forEach((command) -> {
                command.setTimestamp(now);
            });

            // Load knowledge bases in parallel
            List<CompletableFuture<KnowledgeBase>> knowledgeBases = commands.stream()
                    .filter(command -> command.getKnowledgeBaseId() != null)
                    .collect(Collectors.groupingBy((command) -> {
                        // Group commands by knowledgeBaseId
                        return command.getKnowledgeBaseId();
                    })).values().stream().map((kbaseCommands) -> {
                        return CompletableFuture.supplyAsync(() -> {
                            // Load each knowledge base
                            return repository.getKnowledgeBase(kbaseCommands.get(0).getKnowledgeBaseId());
                        });
                    }).collect(Collectors.toList());
            CompletableFuture.allOf(knowledgeBases.toArray(new CompletableFuture[knowledgeBases.size()])).get();

            // Load sessions in parallel
            List<CompletableFuture<Session>> sessions = commands.stream()
                    .filter(command -> command.getSessionId() != null)
                    .collect(Collectors.groupingBy((command) -> {
                        // Group commands by sessionId
                        return command.getSessionId();
                    })).values().stream().map((sessionCommands) -> {
                        return CompletableFuture.supplyAsync(() -> {
                            // Load each session
                            return getSession(sessionCommands.get(0));
                        });
                    }).collect(Collectors.toList());
            CompletableFuture.allOf(sessions.toArray(new CompletableFuture[sessions.size()])).get();

            result = CompletableFuture.runAsync(() -> {
                int requestIndex = 0;
                for (Command command : commands) {
                    if (command.getSessionId() != null) {
                        command.setTimestamp(now);
                        Session session = getSession(command);
                        session.accept(command);
                        store.put(session, command, requestIndex);
                        requestIndex++;
                    }
                }
            }, commandExecutor).thenRun(() -> {
                this.sessions.forEach((id, session) -> {
                    if (session.expired(now)) {
                        if (session.getProcessCount() == 0) {
                            int oldcount = sessions.size();
                            sessions.remove(id);
                            session.dispose();
                            log.info("Session count was " + oldcount + " now " + sessions.size());
                        }
                    }
                });
            });
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        return result;
    }

    public ExecutorService getWorkItemExecutor() {
        return workItemExecutor;
    }
    
    

    public Session getSession(Command command) {
        Properties properties = null;
        Session session = sessions.get(command.getSessionId());
        KnowledgeBase knowledgeBase = null;
        if (command.getKnowledgeBaseId() != null) {
            properties = profiles.getProperties(command.getAccountId(), command.getProfiles());
            knowledgeBase = repository.getKnowledgeBase(command.getKnowledgeBaseId());
        }

        if (session == null && command.getKnowledgeBaseId() != null) {
            session = store.get(command, repository, properties, injector, this);
            sessions.put(command.getSessionId(), session);
        }

        if (session != null && knowledgeBase != null && session.hasChanged(knowledgeBase, properties)) {
            session.update(knowledgeBase, properties, injector, command.getTimestamp(), this);
        }

        return session;
    }

    public void shutdown() {
        store.shutdown();
    }

    private String toJson(Object object) {
        try {
            return json.writeValueAsString(object);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ConcurrentMap<String, Session> getSessions() {
        return sessions;
    }
    
    

}
