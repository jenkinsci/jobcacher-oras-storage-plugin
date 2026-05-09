package io.jenkins.plugins.jobcacher.oras;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Job;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import jenkins.MasterToSlaveFileCallable;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistryItemPath extends ObjectPath {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryItemPath.class);

    private final RegistryClient registry;
    private final String fullName;
    private final String path;

    /**
     * Create a new registry item path.
     * @param registry The registry client
     * @param fullName The full name of the container
     * @param path The path of the item
     */
    public RegistryItemPath(final RegistryClient registry, final String fullName, final String path) {
        this.registry = registry;
        this.fullName = fullName;
        this.path = path;
    }

    public RegistryClient getRegistry() {
        return registry;
    }

    public String getPath() {
        return path;
    }

    @Override
    public RegistryItemPath child(String childPath) throws IOException, InterruptedException {
        return new RegistryItemPath(registry, fullName, String.format("%s/%s", path, childPath));
    }

    @Override
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        target.act(new DownloadFromOciStorage(registry.getConfig(), fullName, path));
    }

    @Override
    public void copyFrom(FilePath source) throws IOException, InterruptedException {
        source.act(new UploadToOciStorage(registry.getConfig(), fullName, path, Logo.getDecodedLogo()));
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return registry.exists(fullName, path);
    }

    @Override
    public void deleteRecursive() throws IOException, InterruptedException {
        registry.delete(fullName, path);
    }

    @Override
    public HttpResponse browse(StaplerRequest2 request, StaplerResponse2 response, Job<?, ?> job, String name) {
        return null;
    }

    /**
     * Master to slave callable that upload a cache to Registry storage.
     */
    private static class UploadToOciStorage extends MasterToSlaveFileCallable<Void> {

        private final RegistryClient.RegistryConfig config;
        private final String path;
        private final String fullName;
        private final byte[] logo;

        public UploadToOciStorage(RegistryClient.RegistryConfig config, String fullName, String path, byte[] logo) {
            this.config = config;
            this.fullName = fullName;
            this.path = path;
            this.logo = logo;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try {
                new RegistryClient(this.config).upload(fullName, path, f.toPath(), logo);
            } catch (Exception e) {
                throw new AbortException("Unable to upload cache to Registry. Details: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Master to slave callable that upload a cache to Registry storage.
     */
    private static class DownloadFromOciStorage extends MasterToSlaveFileCallable<Void> {

        private static final Logger LOG = LoggerFactory.getLogger(DownloadFromOciStorage.class);

        private final RegistryClient.RegistryConfig config;
        private final String fullName;
        private final String path;

        public DownloadFromOciStorage(RegistryClient.RegistryConfig config, String fullName, String path) {
            this.config = config;
            this.fullName = fullName;
            this.path = path;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            try {
                new RegistryClient(this.config).download(fullName, path, f.toPath());
            } catch (Exception e) {
                throw new AbortException("Unable to upload cache to Registry. Details: " + e.getMessage());
            }
            return null;
        }
    }
}
