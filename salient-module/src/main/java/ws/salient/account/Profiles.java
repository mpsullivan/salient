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

package ws.salient.account;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

public interface Profiles {

    public default Set<RemoteRepository> getRemoteRepositories() {
        return Collections.singleton(
                new RemoteRepository.Builder("salient", "default", "http://repo.salient.ws/maven")
                .setPolicy(new RepositoryPolicy(true, "never", "ignore")).build());
    }

    public default Properties getProperties(String accountId, List<String> profiles) {
        return new Properties();
    }

    public default void modified(String accountId) {
    }
    
    public default Map<String, String> getAliases(String accountId, List<String> profiles) {
        return new LinkedHashMap<>();
    }
    

}
