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

package ws.salient.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import org.apache.commons.io.IOUtils;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmazonS3Wagon extends AbstractWagon {

    private final Logger log = LoggerFactory.getLogger(AmazonS3Wagon.class);
    
    private final AmazonS3 s3;

    public AmazonS3Wagon(AmazonS3 s3) {
        this.s3 = s3;
    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
    }

    @Override
    protected void closeConnection() throws ConnectionException {
    }

    @Override
    public void get(String key, File file) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        getIfModifiedSince(key, file, null);
    }

    @Override
    public boolean getIfNewer(String key, File file, long lastModified) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        return getIfModifiedSince(key, file, new Date(lastModified));
    }

    private boolean getIfModifiedSince(String path, File file, Date lastModified) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        GetObjectRequest getObject = new GetObjectRequest(repository.getHost(), getKey(path));
        try {
            if (lastModified != null) {
                getObject.setModifiedSinceConstraint(lastModified);
            }
            S3Object s3Object = s3.getObject(getObject);
            
            if (s3Object != null) {
                log.info(file.getAbsolutePath() + ": " + String.valueOf(s3Object.getObjectMetadata().getContentLength()));
                try (InputStream in = s3Object.getObjectContent(); FileOutputStream fileOut = new FileOutputStream(file)) {
                    IOUtils.copy(in, fileOut);
                }
                return true;
            }
            return false;
        } catch (AmazonS3Exception ex) {
            if (ex.getErrorCode().equals("NoSuchKey") || ex.getErrorCode().equals("AccessDenied")) {
                log.warn("s3://" + getObject.getBucketName() + "/" + getObject.getKey(), ex);
                throw new ResourceDoesNotExistException("s3://" + getObject.getBucketName() + "/" + getObject.getKey(), ex);
            }
            throw new TransferFailedException(ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new TransferFailedException(ex.getMessage(), ex);
        }
    }

    @Override
    public void put(File file, String path) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        try {
            PutObjectRequest putObject = new PutObjectRequest(repository.getHost(), getKey(path), file);
            s3.putObject(putObject);
        } catch (AmazonS3Exception ex) {
            throw new AuthorizationException(ex.getErrorCode(), ex);
        }
    }

    private String getKey(String path) {
        return new StringBuilder().append(repository.getBasedir().substring(1))
                .append("/").append(path).toString();
    }

}
