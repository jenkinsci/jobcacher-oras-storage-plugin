package io.jenkins.plugins.jobcacher.oras;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import land.oras.ContainerRef;
import land.oras.exception.OrasException;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class RegistryItemStorage extends ItemStorage<RegistryItemPath> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Logger LOGGER = LoggerFactory.getLogger(RegistryItemStorage.class);

    private String storageCredentialId;
    private String registryUrl;
    private String namespace;

    @DataBoundConstructor
    public RegistryItemStorage() {}

    @DataBoundSetter
    public void setStorageCredentialId(String storageCredentialId) {
        this.storageCredentialId = storageCredentialId;
    }

    @DataBoundSetter
    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getStorageCredentialId() {
        return storageCredentialId;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public RegistryItemPath getObjectPath(Item item, String path) {
        return new RegistryItemPath(createClient(), String.format("%s/%s", namespace, item.getFullName()), path);
    }

    @Override
    public RegistryItemPath getObjectPathForBranch(Item item, String path, String branch) {
        String branchPath = new File(item.getFullName()).getParent() + "/" + branch;
        return new RegistryItemPath(createClient(), String.format("%s/%s", namespace, branchPath), path);
    }

    private RegistryClient createClient() {
        return new RegistryClient(registryUrl, namespace, getCredentials(storageCredentialId));
    }

    public static RegistryItemStorage get() {
        return ExtensionList.lookupSingleton(RegistryItemStorage.class);
    }

    public static StandardUsernamePasswordCredentials getCredentials(String credentialsId) {
        if (credentialsId == null) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM2, Collections.emptyList()),
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId),
                        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)));
    }

    /**
     * ORAS storage descriptor.
     */
    @Extension
    @Symbol("oras")
    public static final class DescriptorImpl extends ItemStorageDescriptor<RegistryItemPath> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "oras";
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]", "unused"})
        public ListBoxModel doFillStorageCredentialIdItems(@AncestorInPath Item item) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(get().getStorageCredentialId());
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(get().getStorageCredentialId());
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            item,
                            StandardUsernameCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.instanceOf(StandardUsernameCredentials.class))
                    .includeCurrentValue(get().getStorageCredentialId());
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]", "unused"})
        public FormValidation doCheckRegistryUrl(@QueryParameter String registryUrl) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            FormValidation ret = FormValidation.ok();
            if (registryUrl == null || registryUrl.isBlank()) {
                ret = FormValidation.error("Server url cannot be blank");
            } else {
                try {
                    ContainerRef.parse("%s/library/test:latest".formatted(registryUrl));
                } catch (OrasException e) {
                    ret = FormValidation.error("Registry url doesn't seem valid.");
                }
            }
            return ret;
        }

        @RequirePOST
        @SuppressWarnings({"unused"})
        public FormValidation doValidateRegistryConfig(
                @QueryParameter("registryUrl") final String registryUrl,
                @QueryParameter("storageCredentialId") final String storageCredentialId,
                @QueryParameter("namespace") final String namespace) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (registryUrl == null || registryUrl.isBlank()) {
                return FormValidation.error("Fields required");
            }
            // Namespace cannot end with /
            if (namespace.endsWith("/")) {
                return FormValidation.error("Namespace cannot end with /");
            }
            try {
                RegistryClient client = new RegistryClient(registryUrl, namespace, getCredentials(storageCredentialId));
                client.testConnection();
                LOGGER.debug("Registry configuration validated");

            } catch (Exception e) {
                LOGGER.error("Unable to connect to Registry. Please check the server url and credentials", e);
                return FormValidation.error(
                        "Unable to connect to Registry. Please check the server url and credentials : "
                                + e.getMessage());
            }

            return FormValidation.ok("Success");
        }
    }
}
