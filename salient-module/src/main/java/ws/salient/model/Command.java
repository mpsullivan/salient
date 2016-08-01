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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import ws.salient.model.commands.AbortWorkItem;
import ws.salient.model.commands.CompleteWorkItem;
import ws.salient.model.commands.Insert;
import ws.salient.model.commands.ModifyProfile;
import ws.salient.model.commands.WorkItemException;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.PROPERTY, property = "command")
@JsonSubTypes({
    @Type(value = Insert.class, name = "insert"),
    @Type(value = CompleteWorkItem.class, name = "completeWorkItem"),
    @Type(value = AbortWorkItem.class, name = "abortWorkItem"),
    @Type(value = WorkItemException.class, name = "workItemException"),
    @Type(value = ModifyProfile.class, name = "modifyProfile")
})
public class Command implements Serializable {

    private String accountId;
    private List<String> profiles;
    private String sessionId;
    private String knowledgeBaseId;
    private Instant timestamp;

    public Command(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public Command() {
    }

    public String getAccountId() {
        return accountId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Command withKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
        return this;
    }
    
    public List<String> getProfiles() {
        return profiles;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public Command withTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public void setProfiles(List<String> profiles) {
        this.profiles = profiles;
    }

    public Command withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public Command withProfiles(List<String> profiles) {
        this.profiles = profiles;
        return this;
    }

    public Command withProfile(String profile) {
        if (profiles == null) {
            profiles = new LinkedList();
        }
        profiles.add(profile);
        return this;
    }

    public Command withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

}
