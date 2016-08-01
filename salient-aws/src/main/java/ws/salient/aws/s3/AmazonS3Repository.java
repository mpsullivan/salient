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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.wagon.Wagon;
import org.drools.compiler.kie.builder.impl.AbstractKieModule;
import org.drools.compiler.kie.builder.impl.KieRepositoryImpl;
import org.drools.compiler.kproject.ReleaseIdImpl;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.scanner.Aether;
import org.kie.scanner.MavenRepository;
import static org.kie.scanner.MavenRepository.toFileName;
import org.nustaq.serialization.FSTConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.salient.aws.databind.AmazonModule;
import ws.salient.knowledge.KnowledgeBase;
import ws.salient.knowledge.KnowledgeRepository;

public class AmazonS3Repository implements KnowledgeRepository {

    private final static Logger log = LoggerFactory.getLogger(AmazonS3Repository.class);
    private final KieServices kie;
    private final Aether aether;
    private Set<RemoteRepository> repositories;
    private final ConcurrentMap<String, KnowledgeBase> knowledgeBases;

    public AmazonS3Repository() {
        this(Aether.getAether());
    }
    
    public AmazonS3Repository(Aether aether) {
        this.aether = aether;
        kie = KieServices.Factory.get();
        knowledgeBases = new ConcurrentHashMap();
        repositories = new LinkedHashSet();
    }

    public AmazonS3Repository(AmazonS3 s3) {
        this(initAether(s3));
    }
    
    public void setRemoteRepositories(Set<RemoteRepository> remoteRepositories) {
        this.repositories = remoteRepositories;
        aether.getRepositories().addAll(repositories);
    }

    public AbstractKieModule getKieModule(ReleaseId releaseId, Collection<RemoteRepository> repositories) {
        resolveDependencies(releaseId.toExternalForm(), repositories);
        KieRepositoryImpl repository = (KieRepositoryImpl) kie.getRepository();
        return (AbstractKieModule) repository.getKieModule(releaseId);
    }

    public void putKieModule(AbstractKieModule kmodule, RemoteRepository repository) {

        ReleaseId releaseId = kmodule.getReleaseId();

        MavenRepository.getMavenRepository().deployArtifact(releaseId, kmodule, new File(((ReleaseIdImpl) releaseId).getPomXmlPath()));

        File jar = new File(System.getProperty("java.io.tmpdir"), toFileName(releaseId, null) + ".jar");
        File pom = new File(System.getProperty("java.io.tmpdir"), toFileName(releaseId, null) + ".pom");

        Artifact jarArtifact = new DefaultArtifact(releaseId.getGroupId(), releaseId.getArtifactId(), "jar", releaseId.getVersion());
        jarArtifact = jarArtifact.setFile(jar);

        Artifact pomArtifact = new SubArtifact(jarArtifact, "", "pom");
        pomArtifact = pomArtifact.setFile(pom);

        DeployRequest deployRequest = new DeployRequest();
        deployRequest
                .addArtifact(jarArtifact)
                .addArtifact(pomArtifact)
                .setRepository(repository);

        try {
            Aether.getAether().getSystem().deploy(Aether.getAether().getSession(), deployRequest);
        } catch (DeploymentException e) {
            throw new RuntimeException(e);
        }
    }

    private static Aether initAether(AmazonS3 s3) {
        Aether aether = Aether.getAether();
        DefaultRepositorySystem system = (DefaultRepositorySystem) aether.getSystem();
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.addService(TransporterFactory.class, WagonTransporterFactory.class);
        locator.setServices(WagonProvider.class, new WagonProvider() {
            @Override
            public Wagon lookup(String protocol) throws Exception {
                if ("s3".equals(protocol)) {
                    return new AmazonS3Wagon(s3);
                }
                return null;
            }

            @Override
            public void release(Wagon wagon) {
            }
        });
        system.initService(locator);
        return aether;
    }

    public void resolveDependencies(String dependency, Collection<RemoteRepository> repositories) {
        Artifact artifact = new DefaultArtifact( dependency );
        CollectRequest collectRequest = new CollectRequest();
        Dependency root = new Dependency( artifact, "" );
        collectRequest.setRoot( root );
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);
        repositories.stream().forEach((repo) -> {
            collectRequest.addRepository(repo);
        });
        try {
            aether.getSystem().resolveDependencies(aether.getSession(), dependencyRequest);
            log.info(dependency);
        } catch (DependencyResolutionException e) {
            log.warn("Unable to resolve dependency: " + dependency);
        }
    }
    
    public void resolveArtifact(String dependency, Collection<RemoteRepository> repositories) {
        Artifact artifact = new DefaultArtifact(dependency);
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);
        repositories.stream().forEach((repo) -> {
            artifactRequest.addRepository(repo);
        });
        try {
            aether.getSystem().resolveArtifact(aether.getSession(), artifactRequest);
        } catch (ArtifactResolutionException e) {
            log.warn("Unable to resolve artifact: " + dependency);
        }
    }
    
    @Override
    public KnowledgeBase getKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBases.computeIfAbsent(knowledgeBaseId, (id) -> {
            ReleaseId releaseId = KnowledgeRepository.getReleaseId(knowledgeBaseId);
            AbstractKieModule module = getKieModule(releaseId, repositories);
            KieContainer container = kie.newKieContainer(releaseId);
            KieBase base = container.getKieBase(KnowledgeRepository.getKnowledgeBaseName(knowledgeBaseId));
            FSTConfiguration config = FSTConfiguration.createDefaultConfiguration();
            config.setShareReferences(true);
            config.setForceSerializable(false);
            config.setClassLoader(container.getClassLoader());
            ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            json.registerModules(ObjectMapper.findModules(container.getClassLoader()));
            json.registerModules(new AmazonModule());
            return new KnowledgeBase(KnowledgeRepository.getKnowledgeBaseName(knowledgeBaseId)).withBase(base).withContainer(container).withJson(json).withModule(module).withReleaseId(releaseId).withSerializer(config);
        });
        return knowledgeBase;
    }
}
