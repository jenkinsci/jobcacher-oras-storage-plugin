package io.jenkins.plugins.jobcacher.oras;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Label;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.slaves.DumbSlave;
import java.io.Closeable;
import java.util.Collections;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import land.oras.utils.ZotUnsecureContainer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Run some registry test using realm jenkins rule
 */
public class PipelineTests {

    @Test
    @WithJenkins
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testUploadOnAgent(JenkinsRule rule) throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        try (ZotUnsecureContainer container = new ZotUnsecureContainer().withStartupAttempts(3)) {

            container.start();

            // Configure RegistryItemStorage
            RegistryItemStorage itemStorage = new RegistryItemStorage();
            itemStorage.setRegistryUrl(container.getRegistry());
            itemStorage.setStorageCredentialId(null);
            itemStorage.setNamespace("caches");
            GlobalItemStorage.get().setStorage(itemStorage);

            // Create agent and test upload on agent
            rule.createOnlineSlave(Label.get("agent"));
            String pipelineScript = """
                pipeline {
                    agent {
                        label 'agent'
                    }
                    stages {
                        stage('Upload') {
                            steps {
                                sh 'mkdir -p my-cache && echo "Hello, World!" > my-cache/hello1.txt'
                                cache(maxCacheSize: 250, caches: [
                                        arbitraryFileCache(path: 'my-cache')
                                ]) {
                                    sh 'echo "Hello, World!" > my-cache/hello2.txt'
                                }
                            }
                        }
                    }
                }
                """;

            // Create job, run it and assert logs
            WorkflowJob job = rule.createProject(WorkflowJob.class);
            job.setDefinition(new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(pipelineScript, true));
            rule.buildAndAssertSuccess(job);
        }
    }

    @Test
    @WithJenkins
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testOnExternalSSHAgent(JenkinsRule jenkins) throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        try (ZotUnsecureContainer container = new ZotUnsecureContainer().withStartupAttempts(3)) {

            container.start();

            // Configure RegistryItemStorage
            RegistryItemStorage itemStorage = new RegistryItemStorage();
            itemStorage.setRegistryUrl(container.getRegistry());
            itemStorage.setStorageCredentialId(null);
            itemStorage.setNamespace("caches");
            GlobalItemStorage.get().setStorage(itemStorage);

            try (ContainerAgent agent = setupContainerAgent(jenkins)) {

                String pipelineScript = """
                        pipeline {
                            agent {
                                label 'test-agent'
                            }
                            stages {
                                stage('Upload') {
                                    steps {
                                        sh 'mkdir -p my-cache && echo "Hello, World!" > my-cache/hello1.txt'
                                        cache(maxCacheSize: 250, caches: [
                                                arbitraryFileCache(path: 'my-cache')
                                        ]) {
                                            sh 'echo "Hello, World!" > my-cache/hello2.txt'
                                        }
                                    }
                                }
                            }
                        }
                        """;
                WorkflowJob job = jenkins.createProject(WorkflowJob.class);
                job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
                jenkins.buildAndAssertSuccess(job);
            }
        }
    }

    @SuppressWarnings("resource")
    private static ContainerAgent setupContainerAgent(JenkinsRule jenkins) throws Exception {

        // Start container
        GenericContainer<?> container = new GenericContainer<>(new ImageFromDockerfile("container-agent", false)
                        .withFileFromClasspath("Dockerfile", "docker/Dockerfile"))
                .withExposedPorts(22);
        container.start();

        // Setup credentials to connect to container
        StandardUsernameCredentials credentials = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "test-credentials", "", "root", "password");
        SystemCredentialsProvider.getInstance()
                .getDomainCredentialsMap()
                .put(Domain.global(), Collections.singletonList(credentials));

        // Create agent and connect to it
        final SSHLauncher launcher =
                new SSHLauncher(container.getHost(), container.getMappedPort(22), "test-credentials");
        launcher.setSshHostKeyVerificationStrategy(new NonVerifyingKeyVerificationStrategy());
        DumbSlave dumbAgent = new DumbSlave("test-node", "/home/jenkins/agent", launcher);
        dumbAgent.setNodeName("test-agent");
        dumbAgent.setNumExecutors(1);
        dumbAgent.setLabelString("test-agent");
        jenkins.jenkins.addNode(dumbAgent);
        jenkins.waitOnline(dumbAgent);

        return new ContainerAgent(container, dumbAgent, jenkins);
    }

    public static class ContainerAgent implements Closeable {

        private final GenericContainer<?> container;
        private final DumbSlave agent;
        private final JenkinsRule rule;

        public ContainerAgent(GenericContainer<?> container, DumbSlave agent, JenkinsRule rule) {
            this.container = container;
            this.agent = agent;
            this.rule = rule;
        }

        @Override
        public void close() {
            if (agent != null) {
                try {
                    rule.disconnectSlave(agent);
                } catch (Exception e) {
                    // ignore
                }
            }
            if (container != null) {
                container.stop();
            }
        }
    }
}
