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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Properties;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.time.SessionClock;
import ws.salient.knowledge.KnowledgeBase;

public class SessionModule extends AbstractModule {

    private final Properties properties;
    private final KnowledgeBase knowledgeBase;
    private final KieSession ksession;

    public SessionModule(Properties properties, KnowledgeBase knowledgeBase) {
        this.properties = properties;
        this.knowledgeBase = knowledgeBase;
        KieSessionConfiguration config = KieServices.Factory.get().newKieSessionConfiguration();
        config.setOption(ClockTypeOption.get("pseudo"));
        ksession = (StatefulKnowledgeSessionImpl) knowledgeBase.getBase().newKieSession(config, null);
    }
    
    @Override
    public void configure() {
        
    }
    
    @Provides
    public Properties provideProperties() {
        return properties;
    }
    
    @Provides
    public ObjectMapper provideObjectMapper() {
        return knowledgeBase.getJson();
    }
    
    @Provides
    public KieSession provideKieSession() {
        return ksession;
    }
    
    @Provides
    public KieContainer provideKieContainer() {
        return knowledgeBase.getContainer();
    }
    
    @Provides
    public KieBase provideKieBase() {
        return knowledgeBase.getBase();
    }
    
    @Provides
    public KieModule provideKieModule() {
        return knowledgeBase.getModule();
    }
    
    @Provides
    public ZoneId provideZoneId() {
        if (properties.containsKey("zone.offsetId")) {
            return ZoneOffset.of(properties.getProperty("zone.offsetId"));
        } else {
            return ZoneOffset.UTC;
        }
    }
    
    @Provides
    public Locale provideLocale() {
        Locale.Builder builder = new Locale.Builder();
        if (properties.containsKey("locale.language")) {
            builder.setLanguage(properties.getProperty("locale.language"));
            if (properties.containsKey("locale.script")) {
                builder.setScript(properties.getProperty("locale.script"));
            }
            if (properties.containsKey("locale.region")) {
                builder.setRegion(properties.getProperty("locale.region"));
            }
            return builder.build();
        }
        return Locale.US;
    }
    
    @Provides
    public SessionClock provideSessionClock() {
        return ksession.getSessionClock();
    }
    
    @Provides
    public ClassLoader provideClassLoader() {
        return knowledgeBase.getContainer().getClassLoader();
    }
    
}
