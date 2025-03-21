package io.jenkins.plugins.jobcacher.oras;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void shouldSupportConfigurationAsCode(JenkinsConfiguredWithCodeRule jenkinsRule) throws Exception {
        RegistryItemStorage itemStorage =
                (RegistryItemStorage) GlobalItemStorage.get().getStorage();
        assertThat(itemStorage.getStorageCredentialId(), is("the-credentials-id"));
        assertThat(itemStorage.getRegistryUrl(), is("localhost:5000"));
        assertThat(itemStorage.getNamespace(), is("caches"));
    }
}
