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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.eclipse.aether.repository.RemoteRepository;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Settings {

    private Map<String, Profile> profiles;
    private List<String> activeProfiles;
    private Date lastModified;

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public void setProfiles(Map<String, Profile> profiles) {
        this.profiles = profiles;
    }

    public void setActiveProfiles(List<String> activeProfiles) {
        this.activeProfiles = activeProfiles;
    }

    public Settings withProfile(String id, Profile profile) {
        if (profiles == null) {
            profiles = new LinkedHashMap();
        }
        profiles.put(id, profile);
        return this;
    }

    public Settings withActiveProfile(String id) {
        if (activeProfiles == null) {
            activeProfiles = new LinkedList();
        }
        activeProfiles.add(id);
        return this;
    }
    
    public Map<String, String> getAliases(Collection<String> profileIds) {
        Map<String, String> aliases = new LinkedHashMap();
        getProfiles(profileIds).forEach(profile -> {
            aliases.putAll(profile.getAliases());
        });
        return aliases;
    }

    public Properties getProperties(Collection<String> profileIds) {
        Properties properties = new Properties();
        getProfiles(profileIds).forEach(profile -> {
            properties.putAll(profile.getProperties());
        });
        return properties;
    }

    public Set<RemoteRepository> getRemoteRepositories() {
        return getRemoteRepositories(null);
    }
    
    public Set<RemoteRepository> getRemoteRepositories(Collection<String> profileIds) {
        Set<RemoteRepository> repositories = new LinkedHashSet();
        getProfiles(profileIds).forEach(profile -> {
            profile.getRepositories().forEach((repository) -> {
                repositories.add(repository.toRemoteRepository());
            });
        });
        return repositories;
    }

    private List<Profile> getProfiles(Collection<String> profileIds) {
        List<Profile> selectedProfiles = new LinkedList();
        Optional.ofNullable(activeProfiles).orElse(Collections.EMPTY_LIST).forEach((id) -> {
            selectedProfiles.add(this.profiles.getOrDefault(id, new Profile()));
        });
        if (profileIds != null && this.profiles != null) {
            profileIds.forEach((id) -> {
                selectedProfiles.add(this.profiles.getOrDefault(id, new Profile()));
            });
        }
        return selectedProfiles;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return "Settings{" + "profiles=" + profiles + ", activeProfiles=" + activeProfiles + '}';
    }

}
