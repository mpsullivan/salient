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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class Module {
    
    private Distribution distribution;
    
    private Map<String, String> resources;
    
    private Set<String> dependencies;
    
    private Set<String> includes;
    
    private String name;

    public Module(String name) {
        this.name = name;
    }

    public Module() {
    }

    public Set<String> getIncludes() {
        return includes;
    }

    public void setIncludes(Set<String> includes) {
        this.includes = includes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }    

    public Map<String, String> getResources() {
        return resources;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }

    public void setResources(Map<String, String> resources) {
        this.resources = resources;
    }

    public void setDependencies(Set<String> dependencies) {
        this.dependencies = dependencies;
    }

    public void setDistribution(Distribution distribution) {
        this.distribution = distribution;
    }
    
    public Module withDistribution(Distribution distribution) {
        this.distribution = distribution;
        return this;
    }
    
    public Module withResource(String path, String resource) {
        if (resources == null) {
            resources = new LinkedHashMap();
        }
        resources.put(path, resource);
        return this;
    }

    public Module withDependency(String dependency) {
        if (dependencies == null) {
            dependencies = new LinkedHashSet();
        }
        dependencies.add(dependency);
        return this;
    }
    
    public Module withInclude(String include) {
        if (includes == null) {
            includes = new LinkedHashSet();
        }
        includes.add(include);
        return this;
    }

    @Override
    public String toString() {
        return "Module{" + "distribution=" + distribution + ", resources=" + resources + ", dependencies=" + dependencies + ", includes=" + includes + ", name=" + name + '}';
    }
    
}
