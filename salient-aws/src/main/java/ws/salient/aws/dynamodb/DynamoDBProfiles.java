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

package ws.salient.aws.dynamodb;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.salient.account.Profiles;
import ws.salient.model.Profile;
import ws.salient.model.Repository;
import ws.salient.model.Settings;

public class DynamoDBProfiles implements Profiles {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBProfiles.class);
    
    private final static String ROOT_ACCOUNT_ID = "root";

    private final ConcurrentMap<String, Settings> accounts;

    private final DynamoDB dynamodb;
    private final ObjectMapper json;
    private final AWSKMS kms;

    public DynamoDBProfiles(DynamoDB dynamodb, AWSKMS kms, ObjectMapper json) {
        this.kms = kms;
        this.dynamodb = dynamodb;
        this.json = json;
        accounts = new ConcurrentHashMap();
    }

    private Settings getSettings(String accountId) {
        return accounts.computeIfAbsent(accountId, (id) -> {
            Settings settings = new Settings();
            ItemCollection<QueryOutcome> items = dynamodb.getTable("SalientProfile")
                    .query(new QuerySpec().withHashKey("accountId", id));
            items.pages().forEach((page) -> {
                page.iterator().forEachRemaining((item) -> {
                    try {
                        Profile profile = new Profile();
                        if (item.hasAttribute("aliases")) {
                            profile.setAliases(json.readValue(item.getJSON("aliases"), Map.class));
                        }
                        if (item.hasAttribute("properties")) {
                            if (item.get("properties") instanceof byte[]) {
                                log.info("Decrypt profile " + item.getString("profileName") );
                                DecryptResult decrypt = kms.decrypt(new DecryptRequest().addEncryptionContextEntry("accountId", accountId).withCiphertextBlob(ByteBuffer.wrap(item.getBinary("properties"))));
                                profile.setProperties(json.readValue(decrypt.getPlaintext().array(), Properties.class));
                            } else {
                                Properties properties = new Properties();
                                properties.putAll(item.getMap("properties"));
                                profile.setProperties(properties);
                            }
                        }
                        if (item.hasAttribute("repositories")) {
                            profile.setRepositories(json.readValue(item.getJSON("repositories"), json.getTypeFactory()
                                    .constructCollectionType(Set.class, Repository.class)));
                        }
                        String name = item.getString("profileName");
                        Boolean active = item.getBoolean("active");
                        settings.withProfile(name, profile);
                        if (active) {
                            settings.withActiveProfile(name);
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            });
            return settings;
        });
    }

    @Override
    public Set<RemoteRepository> getRemoteRepositories() {
        Map<String, RemoteRepository> repositories = new LinkedHashMap<>();
        repositories.put("salient", new RemoteRepository.Builder("salient", "remote", "http://repo.salient.ws/maven")
                .setPolicy(new RepositoryPolicy(true, "never", "ignore")).build());
        getSettings(ROOT_ACCOUNT_ID).getRemoteRepositories().forEach((repo) -> {
            repositories.put(repo.getId(), repo);
        });
        return new LinkedHashSet(repositories.values());
    }

    @Override
    public Properties getProperties(String accountId, List<String> profiles) {
        Settings settings = getSettings(accountId);
        Properties properties = new Properties();
        properties.putAll(getSettings(ROOT_ACCOUNT_ID).getProperties(profiles));
        properties.putAll(settings.getProperties(profiles));
        return properties;
    }

    @Override
    public Map<String, String> getAliases(String accountId, List<String> profiles) {
        Settings settings = getSettings(accountId);
        Map<String, String> aliases = new LinkedHashMap();
        aliases.putAll(getSettings(ROOT_ACCOUNT_ID).getAliases(profiles));
        aliases.putAll(settings.getAliases(profiles));
        return aliases;
    }

    @Override
    public void modified(String accountId) {
        accounts.remove(accountId);
    }

}
