package io.jenkins.plugins.jobcacher.oras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import land.oras.utils.SupportedAlgorithm;
import org.junit.jupiter.api.Test;

class LogoTest {

    // cat src/main/resources/images/jobcacher-oras.png | base64 -w0 | sha1sum
    private static final String EXPECTED_SHA1 = "sha1:8079f52198ccafb5d7eec6f6bdbba85126ddace4";

    @Test
    void shouldGetLogo() {
        assertEquals(EXPECTED_SHA1, SupportedAlgorithm.SHA1.digest(Logo.LOGO_BASE_64.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(Logo.getDecodedLogo(), "Logo should not be null");
    }
}
