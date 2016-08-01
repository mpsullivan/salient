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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Repository {
    
    private String id;
    private URI url;

    public Repository() {
    }

    public Repository(String id, String url) {
        this.id = id;
        try {
            this.url = new URI(url);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(url, ex);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }
    
    public RemoteRepository toRemoteRepository() {
        return new RemoteRepository.Builder(getId(), "default", getUrl().toString()).setPolicy(new RepositoryPolicy(true, "never", "ignore")).build();
    }
    
    public static Repository fromRemoteRepository(RemoteRepository remote) {
        return new Repository(remote.getId(), remote.getUrl());
    }
    

    @Override
    public String toString() {
        return "Repository{" + "id=" + id + ", url=" + url + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Repository other = (Repository) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }
    
    
    
}
