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

import com.google.inject.Injector;
import java.util.Properties;
import ws.salient.knowledge.KnowledgeBase;
import ws.salient.knowledge.KnowledgeRepository;
import ws.salient.model.Command;

public interface SessionStore {
    
    public default void put(Session session, Command command, int requestIndex) {
    }
    
    public default Session get(Command command, KnowledgeRepository repository, Properties properties, Injector parentInjector, Sessions sessions) {
        Session session = new Session(command.getSessionId());
        KnowledgeBase knowledgeBase = repository.getKnowledgeBase(command.getKnowledgeBaseId());
        session.init(knowledgeBase, properties, parentInjector, command.getTimestamp(), sessions);
        return session;
    }
    
    public default void shutdown() {
    }
    
}
