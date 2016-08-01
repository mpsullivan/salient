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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieContainerImpl;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.nustaq.serialization.FSTConfiguration;

public class ClasspathRepository implements KnowledgeRepository {

    @Override
    public KnowledgeBase getKnowledgeBase(String knowledgeBaseId) {
        KieServices kie = KieServices.Factory.get();
        KieContainerImpl container = (KieContainerImpl) kie.getKieClasspathContainer();
        ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).findAndRegisterModules();
        json.registerModules(ObjectMapper.findModules(container.getClassLoader()));
        KieBase base = container.getKieBase(KnowledgeRepository.getKnowledgeBaseName(knowledgeBaseId));
        InternalKieModule module = container.getKieProject().getKieModuleForKBase(KnowledgeRepository.getKnowledgeBaseName(knowledgeBaseId));
        FSTConfiguration serializer = FSTConfiguration.createDefaultConfiguration();
        serializer.setShareReferences(true);
        serializer.setForceSerializable(false);
        serializer.setClassLoader(container.getClassLoader());
        return new KnowledgeBase(KnowledgeRepository.getKnowledgeBaseName(knowledgeBaseId))
                .withBase(base)
                .withContainer(container)
                .withJson(json)
                .withModule(module)
                .withReleaseId(container.getReleaseId())
                .withSerializer(serializer);
    }

}
