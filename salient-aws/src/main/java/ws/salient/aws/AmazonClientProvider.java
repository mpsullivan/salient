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

package ws.salient.aws;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;


public class AmazonClientProvider extends AbstractModule {

    private Region region;
    private AmazonS3 s3;
    private AWSKMS kms;
    private DynamoDB dynamodb;
    private AWSLambda lambda;

    public AmazonClientProvider(Region region, AmazonS3 s3, AWSKMS kms, DynamoDB dynamodb, AWSLambda lambda) {
        this.region = region;
        this.s3 = s3;
        this.kms = kms;
        this.dynamodb = dynamodb;
        this.lambda = lambda;
    }

    public AmazonClientProvider() {
        Region region = Regions.getCurrentRegion();
        if (region == null) {
            region = Region.getRegion(Regions.US_EAST_1);
        }
        s3 = new AmazonS3Client().withRegion(region);
        kms = new AWSKMSClient().withRegion(region);
        dynamodb = new DynamoDB(new AmazonDynamoDBClient().withRegion(region));
        lambda = new AWSLambdaClient().withRegion(region);
    }
        
    
    
    @Override
    public void configure() {
        
    }
    
    @Provides
    public AWSLambda getAWSLambda() {
        return lambda;
    }

    @Provides
    public Region getRegion() {
        return region;
    }

    @Provides
    public AmazonS3 getAmazonS3() {
        return s3;
    }

    @Provides
    public AWSKMS getAWSKMS() {
        return kms;
    }

    @Provides
    public DynamoDB getDynamoDB() {
        return dynamodb;
    }
    
}
