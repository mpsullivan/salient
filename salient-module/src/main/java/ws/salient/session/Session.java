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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Injector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.drools.core.process.core.datatype.impl.type.ObjectDataType;
import org.drools.core.process.instance.impl.DefaultWorkItemManager;
import org.drools.core.process.instance.impl.WorkItemImpl;
import org.drools.core.time.SessionPseudoClock;
import org.jbpm.bpmn2.handler.WorkItemHandlerRuntimeException;
import org.jbpm.process.core.context.exception.ExceptionScope;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.instance.ProcessRuntimeImpl;
import org.jbpm.process.instance.context.exception.ExceptionScopeInstance;
import org.jbpm.process.instance.impl.DefaultProcessInstanceManager;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.jbpm.workflow.instance.node.WorkItemNodeInstance;
import org.kie.api.definition.process.Node;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.NodeInstanceContainer;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ws.salient.knowledge.KnowledgeBase;
import static ws.salient.knowledge.SerializableStrategy.Format.FST;
import static ws.salient.knowledge.SerializableStrategy.Format.JAVA;
import ws.salient.model.Command;
import ws.salient.model.commands.AbortWorkItem;
import ws.salient.model.commands.CompleteWorkItem;
import ws.salient.model.commands.Insert;
import ws.salient.model.commands.WorkItemException;

public class Session {

    private static final Logger log = LoggerFactory.getLogger(Session.class);

    private final String sessionId;

    private StatefulKnowledgeSessionImpl ksession;
    private KnowledgeBase knowledgeBase;

    private AtomicLong eventCounter;

    private Injector injector;
    private Properties properties;
    private ByteBuffer encryptedKey;
    private SecretKeySpec secretKey;
    private List<AsyncTaskHandler> workItemHandlers = new LinkedList();

    public Session(String sessionId) {
        this.sessionId = sessionId;
    }

    public void init(KnowledgeBase knowledgeBase, Properties properties, Injector parentInjector, Instant instant, Sessions sessions) {
        init(knowledgeBase, properties, parentInjector, instant, null, sessions);
    }

    public void init(KnowledgeBase knowledgeBase, Properties properties, Injector parentInjector, Instant instant, byte[] sessionBytes, Sessions sessions) {
        
        this.knowledgeBase = knowledgeBase;
        this.properties = properties;
        eventCounter = null;
        injector = parentInjector.createChildInjector(new SessionModule(properties, knowledgeBase));
        ksession = (StatefulKnowledgeSessionImpl) injector.getInstance(KieSession.class);
        setGlobals(injector);
        SessionPseudoClock clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime(instant.toEpochMilli(), TimeUnit.MILLISECONDS);
        workItemHandlers = new LinkedList();
        ksession.getKnowledgeBase().getProcesses().forEach((process) -> {
            for (Node node : ((RuleFlowProcess)process).getNodes()) {
                if (node instanceof WorkItemNode) {
                    try {
                        WorkItemNode workItemNode = (WorkItemNode) node;
                        String handlerName = workItemNode.getWork().getName();
                        System.out.println(handlerName);
                        Class handlerType = knowledgeBase.getContainer().getClassLoader().loadClass(handlerName);
                        AsyncTaskHandler handler = new AsyncTaskHandler(sessionId, (WorkItemHandler) injector.getInstance(handlerType), sessions);
                        workItemHandlers.add(handler);
                        ksession.getWorkItemManager().registerWorkItemHandler(handlerName, handler);
                    } catch (ClassNotFoundException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });
        ksession.addEventListener(new DefaultProcessEventListener() {
            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent pnte) {
                ksession.fireAllRules();
            }
        });
        if (sessionBytes != null) {
            try {
                knowledgeBase.getMarshaller().unmarshall(new ByteArrayInputStream(sessionBytes), ksession);
                // Bug in DefaultProcessInstanceManager, doesn't reset processCounter after unmarshal
                Optional<Long> maxId = ksession.getProcessInstances().stream().map(ProcessInstance::getId).max(Long::compare);
                if (maxId.isPresent()) {
                    ProcessRuntimeImpl processRuntime = (ProcessRuntimeImpl) ksession.getProcessRuntime();
                    DefaultProcessInstanceManager processManager = (DefaultProcessInstanceManager) processRuntime.getProcessInstanceManager();
                    Field counterField = processManager.getClass().getDeclaredField("processCounter");
                    counterField.setAccessible(true);
                    AtomicLong counter = (AtomicLong) counterField.get(processManager);
                    counter.set(maxId.get());
                }
            } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException | IOException ex) {
                log.error("Error unmarshalling session: " + sessionId, ex);
                throw new RuntimeException(ex);
            }
        }
    }

    public List<AsyncTaskHandler> getWorkItemHandlers() {
        return workItemHandlers;
    }
    
    public SecretKeySpec getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(SecretKeySpec secretKey) {
        this.secretKey = secretKey;
    }    

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public ByteBuffer getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(ByteBuffer encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    protected void insertAll(List inserts) {
        if (ksession != null) {
            inserts.forEach((object) -> {
                ksession.insert(object);
            });
            ksession.fireAllRules();
        }
    }

    private void setGlobals(Injector injector) {
        Map<String, Class<?>> globalDefintions = ((InternalKnowledgeBase)ksession.getKieBase()).getGlobals();
        globalDefintions.forEach((String key, Class type) -> {
            Object value = injector.getInstance(type);
            ksession.setGlobal(key, value);
        });
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public String getSessionId() {
        return sessionId;
    }

    private WorkItemNode getWorkItemNode(Long workItemId) {
        DefaultWorkItemManager manager = (DefaultWorkItemManager) ksession.getWorkItemManager();
        WorkItemImpl workItem = (WorkItemImpl) manager.getWorkItem(workItemId);
        RuleFlowProcessInstance instance = (RuleFlowProcessInstance) ksession.getProcessInstance(workItem.getProcessInstanceId());
        RuleFlowProcess process = (RuleFlowProcess) instance.getProcess();
        WorkItemNodeInstance workItemNodeInstance = getNodeInstance(workItemId, instance.getNodeInstances());
        WorkItemNode node = (WorkItemNode) workItemNodeInstance.getNode();
        return node;
    }

    protected WorkItemNodeInstance getNodeInstance(Long workItemId, Collection<org.kie.api.runtime.process.NodeInstance> nodeInstances) {
        for (org.kie.api.runtime.process.NodeInstance nodeInstance : nodeInstances) {
            if (nodeInstance instanceof WorkItemNodeInstance) {
                if (((WorkItemNodeInstance) nodeInstance).getWorkItemId() == workItemId) {
                    return (WorkItemNodeInstance) nodeInstance;
                }
            } else if (nodeInstance instanceof NodeInstanceContainer) {
                WorkItemNodeInstance found = getNodeInstance(workItemId, ((NodeInstanceContainer) nodeInstance).getNodeInstances());
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public void handleException(Long workItemId, Exception exception) {
        DefaultWorkItemManager manager = (DefaultWorkItemManager) ksession.getWorkItemManager();
        WorkItemImpl workItem = (WorkItemImpl) manager.getWorkItem(workItemId);
        WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.getProcessInstance(workItem.getProcessInstanceId());
        WorkItemNodeInstance nodeInstance = getNodeInstance(workItem.getId(), processInstance.getNodeInstances());
        ExceptionScopeInstance exceptionScopeInstance = (ExceptionScopeInstance) nodeInstance.resolveContextInstance(ExceptionScope.EXCEPTION_SCOPE, WorkItemHandlerRuntimeException.class.getName());
        exceptionScopeInstance.handleException(WorkItemHandlerRuntimeException.class.getName(),
                new WorkItemHandlerRuntimeException((Throwable) exception));
    }

    public void completeWorkItem(Long workItemId, Map<String, ?> source) {
        try {
            Map<String, Object> result = new LinkedHashMap();
            if (source != null) {
                DefaultWorkItemManager manager = (DefaultWorkItemManager) ksession.getWorkItemManager();
                WorkItemImpl workItem = (WorkItemImpl) manager.getWorkItem(workItemId);
                RuleFlowProcessInstance instance = (RuleFlowProcessInstance) ksession.getProcessInstance(workItem.getProcessInstanceId());
                RuleFlowProcess process = (RuleFlowProcess) instance.getProcess();
                WorkItemNode node = getWorkItemNode(workItemId);
                source.keySet().forEach((field) -> {
                    String outMapping = node.getOutMapping(field);
                    if (outMapping != null) {
                        try {
                            Variable variable = process.getVariableScope().findVariable(outMapping);
                            Class type = knowledgeBase.getContainer().getClassLoader().loadClass( ((ObjectDataType) variable.getType()).getClassName());
                            Object value = source.get(field);
                            if (!value.getClass().isAssignableFrom(type)) {
                                value = knowledgeBase.getJson().convertValue(value, type);
                            }
                            result.put(field, value);
                        } catch (ClassNotFoundException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }
            WorkItemManager manager = ksession.getWorkItemManager();
            manager.completeWorkItem(workItemId, result);
            ksession.fireAllRules();
        } catch (Exception ex) {
            WorkItemManager manager = ksession.getWorkItemManager();
            manager.abortWorkItem(workItemId);
            log.error("Failed to complete workitem", ex);
        }
    }

    public int getProcessCount() {
        return ksession.getProcessInstances().size();
    }

    public void dispose() {
        ksession.dispose();
    }

    public void update(KnowledgeBase knowledgeBase, Properties properties, Injector parentInjector, Instant instant, Sessions sessions) {
        try (ByteArrayOutputStream sessionOut = new ByteArrayOutputStream()) {
            // FST serialization only works if objects are unchanged
            if (this.knowledgeBase.equals(knowledgeBase)) {
                // Use FST serialization for speed
                knowledgeBase.getMarshaller(FST).marshall(sessionOut, ksession);
            } else {
                // Use java serialization for version changes
                knowledgeBase.getMarshaller(JAVA).marshall(sessionOut, ksession);
            }
            this.init(knowledgeBase, properties, parentInjector, instant, sessionOut.toByteArray(), sessions);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean hasChanged(KnowledgeBase knowledgeBase, Properties properties) {
        return !(knowledgeBase.equals(this.knowledgeBase) && properties.equals(this.properties));
    }

    public SessionPseudoClock getClock() {
        return (SessionPseudoClock) ksession.getSessionClock();
    }

    public List inserts(ArrayNode inserts) {
        MDC.put("sessionId", sessionId);
        List objects = new LinkedList();
        inserts.elements().forEachRemaining((node) -> {
            node.fieldNames().forEachRemaining((className) -> {
                try {
                    Object insert = knowledgeBase.getJson().convertValue(node.get(className), knowledgeBase.getContainer().getClassLoader().loadClass(className));
                    objects.add(insert);
                } catch (ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            });
        });
        return objects;
    }

    public final void accept(Command request) {
        Instant instant = request.getTimestamp();
        MDC.put("sessionId", sessionId);
        MDC.put("instant", instant.toString());
        try {
            log.info(knowledgeBase.getJson().writeValueAsString(request));
        } catch (JsonProcessingException ex) {
        }
        long sessionTime = getClock().getCurrentTime();
        long advanceTime = instant.toEpochMilli() - sessionTime;
        getClock().advanceTime(advanceTime, TimeUnit.MILLISECONDS);
        ksession.fireAllRules();
        if (request instanceof Insert) {
            Insert insert = (Insert) request;
            ArrayNode inserts = insert.getObjects();
            List objects = inserts(inserts);
            insertAll(objects);
        } else if (request instanceof CompleteWorkItem) {
            CompleteWorkItem workItem = (CompleteWorkItem) request;
            Long workItemId = workItem.getWorkItemId();
            Map<String, ?> source = workItem.getResult();
            completeWorkItem(workItemId, source);
        } else if (request instanceof AbortWorkItem) {
            AbortWorkItem workItem = (AbortWorkItem) request;
            Long workItemId = workItem.getWorkItemId();
            ksession.getWorkItemManager().abortWorkItem(workItemId);
        } else if (request instanceof WorkItemException) {
            WorkItemException workItem = (WorkItemException) request;
            Long workItemId = workItem.getWorkItemId();
            Exception exception = workItem.getException();
            handleException(workItemId, exception);
        }
        if (eventCounter != null) {
            eventCounter.incrementAndGet();
        }
    }

    public Map<String, Long> getFactCount() {
        return ksession.getFactHandles()
                .stream()
                .map((handle) -> {
                    return ksession.getObject(handle);
                })
                .collect(Collectors.groupingBy((o) -> {
                    return o.getClass().getSimpleName();
                }, Collectors.counting()));
    }

    public boolean store(Command command) {

        boolean store = false;

        if (command instanceof Insert) {
            store = (expired(command.getTimestamp()) || eventCounter == null || eventCounter.get() >= 30);
        }

        if (store) {
            eventCounter = new AtomicLong();
        }
        return store;
    }

    public boolean expired(Instant instant) {
        Instant minsAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
        return (instant != null && instant.isBefore(minsAgo));
    }
    
    public byte[] toByteArray() {
        try (ByteArrayOutputStream sessionOut = new ByteArrayOutputStream()) {
            knowledgeBase.getMarshaller().marshall(sessionOut, ksession);
            byte[] sessionBytes = sessionOut.toByteArray();
            return sessionBytes;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
