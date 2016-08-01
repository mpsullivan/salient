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

import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.salient.aws.databind.AmazonModule;
import ws.salient.aws.dynamodb.DynamoDBProfiles;
import ws.salient.aws.dynamodb.DynamoDBStore;
import ws.salient.aws.s3.AmazonS3Repository;
import ws.salient.model.Command;
import ws.salient.session.Sessions;

public class RecordProcessor implements IRecordProcessor {

    private final static Logger log = LoggerFactory.getLogger(RecordProcessor.class);

    private Sessions sessions;
    private final ObjectMapper json;
    
    public RecordProcessor() {
        json = new ObjectMapper().findAndRegisterModules().registerModules(new AmazonModule());
    }

    @Override
    public void initialize(InitializationInput input) {
        AmazonClientProvider provider = new AmazonClientProvider();
        sessions = new Sessions(new AmazonS3Repository(provider.getAmazonS3()),
                new DynamoDBProfiles(provider.getDynamoDB(), provider.getAWSKMS(), json),
                new DynamoDBStore(provider.getDynamoDB(), provider.getAWSKMS(), json, Executors.newSingleThreadExecutor()),
                Guice.createInjector(provider),
                Executors.newSingleThreadExecutor(),
                ForkJoinPool.commonPool());
    }

    @Override
    public void processRecords(ProcessRecordsInput input) {
        accept(input.getRecords());
    }

    @Override
    public void shutdown(ShutdownInput input) {
        sessions.shutdown();
    }
    
    public void accept(List<Record> records) {
        try {
            List<Command> commands = records.stream()
                    .map((record) -> {
                        try {
                            return json.readValue(record.getData().array(), Command.class);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    })
                    .collect(Collectors.toList());
            sessions.execute(commands).get();
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
    }

}
