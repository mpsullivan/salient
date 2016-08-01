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

package ws.salient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Profile {

    private Properties properties = new Properties();
    private Set<Repository> repositories = new LinkedHashSet();
    private Map<String, String> aliases = new LinkedHashMap();

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Set<Repository> getRepositories() {
        return repositories;
    }

    public void setRepositories(Set<Repository> repositories) {
        this.repositories = repositories;
    }
    
    public Profile withRepositories(Repository repository) {
        if (repositories == null) {
            repositories = new LinkedHashSet();
        }
        repositories.add(repository);
        return this;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public Profile withAlias(String name, String value) {
        if (aliases == null) {
            aliases = new LinkedHashMap();
        }
        aliases.put(name, value);
        return this;
    }

    public Profile withProperty(String name, String value) {
        if (properties == null) {
            properties = new Properties();
        }
        properties.put(name, value);
        return this;
    }

    @Override
    public String toString() {
        return "Profile{properties=" + properties + ", repositories=" + repositories + '}';
    }

}
