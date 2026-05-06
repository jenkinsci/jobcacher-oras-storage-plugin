package io.jenkins.plugins.jobcacher.oras;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * RealJenkinsExtension test to detect classloading issues.
 *
 * <p>Uses a real Jenkins JVM where the plugin is loaded from a JAR file (as in production), which
 * exposes classloading issues that are not visible in regular unit tests. Specifically, this
 * validates the fix for the bug where {@code Paths.get(url.toURI())} throws {@code
 * FileSystemNotFoundException} when the icon image resource is inside a JAR file.
 *
 * @see <a href="https://github.com/jenkinsci/jobcacher-oras-storage-plugin/issues/79">Issue #79</a>
 */
class RegistryClientRealJenkinsTest {

    @RegisterExtension
    static final RealJenkinsExtension rje = new RealJenkinsExtension();

    /**
     * Verifies that the plugin icon resource can be loaded correctly in a real Jenkins JVM, where
     * the plugin is deployed as a JAR. The old (buggy) code used {@code Paths.get(url.toURI())}
     * which throws {@code FileSystemNotFoundException} for {@code jar:} URIs. The fix uses {@code
     * url.openStream()} to copy the resource to a temporary file first.
     */
    @Test
    void testIconResourceLoadingWithRealJenkins() throws Throwable {
        rje.then(RegistryClientRealJenkinsTest::verifyIconResourceLoading);
    }

    private static void verifyIconResourceLoading(JenkinsRule r) throws Throwable {
        URL url = RegistryClient.class.getResource("/images/jobcacher-oras.png");
        assertThat("Icon image resource must be accessible from the plugin JAR classloader", url, notNullValue());

        // Verify the resource can be opened as a stream and written to a temp file.
        // This is the fixed approach; the old code used Paths.get(url.toURI()) which fails
        // with FileSystemNotFoundException when the resource is loaded from a JAR file.
        Path tempPath = null;
        try {
            tempPath = Files.createTempFile("jobcacher-oras-icon", ".png");
            try (InputStream is = url.openStream()) {
                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            assertThat(
                    "Icon image resource must have non-zero size", Files.size(tempPath), greaterThan(0L));
        } finally {
            if (tempPath != null) {
                Files.deleteIfExists(tempPath);
            }
        }
    }
}
