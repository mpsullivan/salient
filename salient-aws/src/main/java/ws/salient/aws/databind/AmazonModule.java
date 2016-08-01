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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.AmazonWebServiceResponse;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.lambda.invoke.LambdaFunctionException;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;


public class AmazonModule extends SimpleModule {
    
    @Override
    public void setupModule(SetupContext context) {
        context.addAbstractTypeResolver(new SimpleAbstractTypeResolver().addMapping(AWSCredentials.class, BasicAWSCredentials.class));
        context.setMixInAnnotations(BasicAWSCredentials.class, BasicAWSCredentialsMixIn.class);
        context.setMixInAnnotations(AmazonWebServiceRequest.class, AmazonWebServiceRequestMixIn.class);
        context.setMixInAnnotations(AmazonWebServiceResponse.class, AmazonWebServiceResponseMixIn.class);        
        // AWS Lambda
        context.setMixInAnnotations(LambdaFunctionException.class, LambdaFunctionExceptionMixIn.class);
        
    }
}
