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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.kms.AWSKMSClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import org.eclipse.aether.repository.RemoteRepository;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;


public class DynamoDBProfilesIT {
    
    DynamoDBProfiles profiles;
    
    @Before
    public void before() {
        profiles = new DynamoDBProfiles(new DynamoDB(new AmazonDynamoDBClient()), new AWSKMSClient(), new ObjectMapper());
    }
    
    @Test
    public void getRemoteRepositories() {
        Set<RemoteRepository> repositories = profiles.getRemoteRepositories();
        assertFalse(repositories.isEmpty());
    }
    
    @Test
    public void getProperties() {
        Properties properties = profiles.getProperties("test", Arrays.asList("demo"));
        assertFalse(properties.isEmpty());
    }
}
