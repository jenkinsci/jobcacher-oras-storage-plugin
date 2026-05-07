package io.jenkins.plugins.jobcacher.oras;

import java.util.logging.Level;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import land.oras.utils.ZotUnsecureContainer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;
import org.testcontainers.DockerClientFactory;

/**
 * Run some registry test using realm jenkins rule
 */
public class RealJenkinsTest {

    @RegisterExtension
    static final RealJenkinsExtension jenkinsExtension = new RealJenkinsExtension().withLogger("land.oras", Level.ALL);

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testUpload() throws Throwable {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        try (ZotUnsecureContainer container = new ZotUnsecureContainer().withStartupAttempts(3)) {
            container.start();
            String registry = container.getRegistry();
            jenkinsExtension.then((RealJenkinsExtension.Step) rule -> uploadCache(rule, registry));
        }
    }

    private static void uploadCache(JenkinsRule rule, String registry) throws Throwable {

        // Configure RegistryItemStorage
        RegistryItemStorage itemStorage = new RegistryItemStorage();
        itemStorage.setRegistryUrl(registry);
        itemStorage.setStorageCredentialId(null);
        itemStorage.setNamespace("caches");
        GlobalItemStorage.get().setStorage(itemStorage);

        // Test upload on controller
        String pipelineScript = """
            pipeline {
                agent {
                    label 'built-in'
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
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        rule.buildAndAssertSuccess(job);
    }
}
