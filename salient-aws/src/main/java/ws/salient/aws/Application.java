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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.kinesis.metrics.interfaces.MetricsLevel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.kie.scanner.embedder.MavenSettings;

public class Application {

    public void run() {
        try {
            String applicationName = "Salient";
            AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
            String workerId = InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID();
            KinesisClientLibConfiguration config = new KinesisClientLibConfiguration(applicationName, "salient", credentialsProvider, workerId).withMetricsLevel(MetricsLevel.NONE)
                    .withInitialPositionInStream(InitialPositionInStream.LATEST).withIdleTimeBetweenReadsInMillis(200);
            Worker worker = new Worker.Builder()
                    .recordProcessorFactory(() -> {
                        return new RecordProcessor();
                    })
                    .config(config)
                    .build();
            worker.run();
        } catch (UnknownHostException ex) {
            throw new RuntimeException(ex);
        }
        unpackRepository("/maven.zip");
    }

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        Application application = new Application();
        application.run();
    }
    
    public static void unpackRepository(String resourceName) {
        try {
            try (ZipInputStream stream = new ZipInputStream(Application.class.getResourceAsStream(resourceName))) {
                String outdir = MavenSettings.getSettings().getLocalRepository();

                System.out.println(outdir);
                ZipEntry entry;
                while ((entry = stream.getNextEntry()) != null) {

                    if (entry.getSize() == 0) {
                        File outpath = new File(outdir + "/" + entry.getName());
                        outpath.mkdirs();
                    } else {
                        File outpath = new File(outdir + "/" + entry.getName());
                        outpath.createNewFile();
                        try (FileOutputStream output = new FileOutputStream(outpath)) {
                            IOUtils.copy(stream, output);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
