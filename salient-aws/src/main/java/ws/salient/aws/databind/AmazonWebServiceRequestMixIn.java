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

package ws.salient.aws.databind;

import com.amazonaws.RequestClientOptions;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.metrics.RequestMetricCollector;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;


public abstract class AmazonWebServiceRequestMixIn {
 
    @JsonIgnore
    public abstract RequestMetricCollector getRequestMetricCollector();
    
    @JsonIgnore
    public abstract AWSCredentials getRequestCredentials();
    
    @JsonIgnore
    public abstract RequestClientOptions getRequestClientOptions();
    
    @JsonIgnore
    public abstract ProgressListener getGeneralProgressListener();
    
    @JsonIgnore
    public abstract Map<String, String> getCustomRequestHeaders();
    
    @JsonIgnore
    public abstract int getReadLimit();
    
}
