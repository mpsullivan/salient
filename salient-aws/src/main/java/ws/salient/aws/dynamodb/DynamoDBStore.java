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

import com.amazonaws.services.dynamodbv2.document.BatchWriteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.Page;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.TableWriteItems;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.model.DataKeySpec;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.amazonaws.services.kms.model.GenerateDataKeyRequest;
import com.amazonaws.services.kms.model.GenerateDataKeyResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.salient.knowledge.KnowledgeBase;
import ws.salient.knowledge.KnowledgeRepository;
import ws.salient.model.Command;
import ws.salient.model.commands.WorkItem;
import ws.salient.session.Session;
import ws.salient.session.SessionStore;
import ws.salient.session.Sessions;


public class DynamoDBStore implements SessionStore {

    private static final DateTimeFormatter NANO_INSTANT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant(9).toFormatter();

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final DynamoDB dynamodb;
    private final ObjectMapper json;
    protected final Queue<Item> sessionsToPut;
    protected final Queue<Item> eventsToPut;
    private final ExecutorService putItemExecutor;
    private final AWSKMS kms;
    private final String transformation;

    public DynamoDBStore(DynamoDB dynamodb, AWSKMS kms, ObjectMapper json, ExecutorService putItemExecutor) {
        this.kms = kms;
        this.dynamodb = dynamodb;
        this.json = json;
        this.transformation = "AES/CBC/PKCS5Padding";
        this.putItemExecutor = putItemExecutor;
        sessionsToPut = new ConcurrentLinkedQueue();
        eventsToPut = new ConcurrentLinkedQueue();
    }

    public void put(Session session, Command command, int requestIndex) {
        try {

            eventsToPut.offer(encrypt(new Item()
                    .withPrimaryKey("sessionId", session.getSessionId(), "timestamp", NANO_INSTANT.format(command.getTimestamp().plusNanos(requestIndex)))
                    .withBinary("command", json.writeValueAsBytes(command)), session.getSecretKey(), "command"));

            if (session.store(command)) {
                byte[] sessionBytes = session.toByteArray();
                byte[] properties = json.writeValueAsBytes(session.getProperties());
                Item item = new Item().withPrimaryKey("sessionId", command.getSessionId(), "timestamp", command.getTimestamp().toString())
                        .withString("accountId", command.getAccountId())
                        .withMap("factCount", session.getFactCount())
                        .withInt("processCount", session.getProcessCount())
                        .withString("knowledgeBaseId", command.getKnowledgeBaseId())
                        .withBinary("session", sessionBytes)
                        .withBinary("properties", properties);

                if (session.getSecretKey() != null) {
                    item.withMap("secretKey", new LinkedHashMap());
                    item.getMap("secretKey").put("encrypted", session.getEncryptedKey());
                    item.getMap("secretKey").put("algorithm", session.getSecretKey().getAlgorithm());
                }

                if (session.getSecretKey() != null) {
                    item = encrypt(item, session.getSecretKey(), "properties", "session");
                }
                sessionsToPut.offer(item);
            }

            putItemExecutor.execute(() -> {
                List<Item> eventItems = new LinkedList();
                Item eventItem = eventsToPut.poll();
                while (eventItem != null) {
                    eventItems.add(eventItem);
                    eventItem = eventsToPut.poll();
                }
                if (!eventItems.isEmpty()) {
                    TableWriteItems eventWriteItems = new TableWriteItems("SalientSessionEvent").withItemsToPut(eventItems);
                    log.info("Storing events: " + eventItems.size());
                    BatchWriteItemOutcome result = dynamodb.batchWriteItem(eventWriteItems);
                    if (!result.getUnprocessedItems().isEmpty()) {
                        log.error("Unprocessed items: " + result.toString());
                    }
                }
                Map<String, Item> sessionItems = new LinkedHashMap();
                Item sessionItem = sessionsToPut.poll();
                while (sessionItem != null) {
                    // Only store latest session item
                    sessionItems.put(sessionItem.getString("sessionId"), sessionItem);
                    sessionItem = sessionsToPut.poll();
                }
                if (!sessionItems.isEmpty()) {
                    TableWriteItems sessionWriteItems = new TableWriteItems("SalientSession").withItemsToPut(sessionItems.values());
                    log.info("Storing sessions: " + sessionItems.size());
                    BatchWriteItemOutcome result = dynamodb.batchWriteItem(sessionWriteItems);
                    if (!result.getUnprocessedItems().isEmpty()) {
                        log.error("Unprocessed items: " + result.toString());
                    }
                }
            });
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Session get(Command command, KnowledgeRepository repository, Properties properties, Injector parentInjector, Sessions sessions) {
        return get(command, repository, properties, parentInjector, sessions,
                new QuerySpec().withHashKey("sessionId", command.getSessionId())
                .withScanIndexForward(false)
                .withConsistentRead(true)
                .withMaxResultSize(1));
    }

    public Session get(Command command, KnowledgeRepository repository, Properties properties, Injector parentInjector, Sessions sessions, QuerySpec sessionQuery) {
        String sessionId = command.getSessionId();
        String accountId = command.getAccountId();

        SecretKeySpec secretKey;
        ByteBuffer encryptedKey;
        Session session = new Session(sessionId);
        Page<Item, QueryOutcome> page = dynamodb.getTable("SalientSession")
                .query(sessionQuery).firstPage();
        if (page != null && page.size() > 0) {

            try {
                Item result = page.iterator().next();

                encryptedKey = ByteBuffer.wrap((byte[]) result.getMap("secretKey").get("encrypted"));
                if (encryptedKey != null) {
                    DecryptResult decrypt = kms.decrypt(new DecryptRequest()
                            .addEncryptionContextEntry("accountId", accountId)
                            .addEncryptionContextEntry("sessionId", sessionId)
                            .withCiphertextBlob(encryptedKey));
                    byte[] key = decrypt.getPlaintext().array();
                    secretKey = new SecretKeySpec(key, (String) result.getMap("secretKey").get("algorithm"));
                } else {
                    secretKey = null;
                }

                result = decrypt(result, secretKey, "properties", "session");

                properties = json.readValue(result.getBinary("properties"), Properties.class);
                String knowledgeBaseId = result.getString("knowledgeBaseId");
                KnowledgeBase knowledgeBase = repository.getKnowledgeBase(knowledgeBaseId);
                String timestamp = result.getString("timestamp");

                session.init(knowledgeBase, properties, parentInjector, Instant.parse(timestamp), result.getBinary("session"), sessions);

                int processCount = session.getProcessCount();

                List<Item> eventItems = new LinkedList();
                ItemCollection<QueryOutcome> query = dynamodb.getTable("SalientSessionEvent").query(new QuerySpec().withConsistentRead(true).withHashKey("sessionId", sessionId)
                        .withRangeKeyCondition(new RangeKeyCondition("timestamp").gt(timestamp)));
                query.pages().forEach((eventPage) -> {
                    eventPage.forEach((eventItem) -> {
                        eventItems.add(eventItem);
                    });
                });

                List<Command> commands = new LinkedList();

                eventItems.forEach((eventItem) -> {
                    try {
                        eventItem = decrypt(eventItem, secretKey, "command");
                        byte[] value = eventItem.getBinary("command");
                        ObjectInputStream objectIn = new ObjectInputStream(new ByteArrayInputStream(value)) {
                            protected Class<?> resolveClass(ObjectStreamClass desc)
                                    throws IOException,
                                    ClassNotFoundException {
                                return session.getKnowledgeBase().getContainer().getClassLoader().loadClass(desc.getName());
                            }
                        };
                        Command event = (Command) objectIn.readObject();
                        if (event instanceof WorkItem) {
                            session.getWorkItemHandlers().forEach((handler) -> {
                                handler.getCompletedWorkItemIds().add(((WorkItem) event).getWorkItemId());
                            });
                        }
                        commands.add(event);
                    } catch (ClassNotFoundException | IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
                commands.forEach((event) -> {
                    session.accept(event);
                });
                session.getWorkItemHandlers().forEach((handler) -> {
                    handler.getCompletedWorkItemIds().clear();
                });

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            GenerateDataKeyResult dataKey = generateEncryptionKey(accountId, sessionId);
            byte[] key = dataKey.getPlaintext().array();
            secretKey = new SecretKeySpec(key, "AES");
            encryptedKey = dataKey.getCiphertextBlob();
            KnowledgeBase knowledgeBase = repository.getKnowledgeBase(command.getKnowledgeBaseId());
            session.init(knowledgeBase, properties, parentInjector, command.getTimestamp(), sessions);
        }
        session.setEncryptedKey(encryptedKey);
        session.setSecretKey(secretKey);
        return session;
    }

    public void shutdown() {
        try {
            if (putItemExecutor != null) {
                log.info("Shutting down putItems");
                putItemExecutor.shutdown();
                putItemExecutor.awaitTermination(60, TimeUnit.SECONDS);
                log.info("PutItems completed");
            }
        } catch (Exception ex) {
            log.error("Shutdown failed.", ex);
        }
    }

    public GenerateDataKeyResult generateEncryptionKey(String accountId, String sessionId) {
        GenerateDataKeyResult generateDataKey = kms.generateDataKey(
                new GenerateDataKeyRequest()
                .withKeySpec(DataKeySpec.AES_128)
                .addEncryptionContextEntry("accountId", accountId)
                .addEncryptionContextEntry("sessionId", sessionId)
                .withKeyId("alias/salient"));
        return generateDataKey;
    }
    
    public Item encrypt(Item item, SecretKeySpec key, String... attributes) {
        try {
            if (key != null) {
                Cipher cipher = Cipher.getInstance(transformation);
                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] iv = cipher.getIV();
                for (String attribute : attributes) {
                    byte[] value = item.getBinary(attribute);
                    item.withBinary(attribute, cipher.doFinal(value));
                }
                item.withMap("cipher", new LinkedHashMap());
                item.getMap("cipher").put("transformation", transformation);
                item.getMap("cipher").put("iv", iv);
            }
            return item;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Item decrypt(Item item, SecretKeySpec key, String... attributes) {
        try {
            if (key != null) {
                String transformation = (String) item.getMap("cipher").get("transformation");
                byte[] iv = (byte[]) item.getMap("cipher").get("iv");
                Cipher cipher = Cipher.getInstance(transformation);
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                for (String attribute : attributes) {
                    byte[] value = item.getBinary(attribute);
                    item.withBinary(attribute, cipher.doFinal(value));
                }
            }
            return item;

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new RuntimeException(ex);
        }
    }

}
