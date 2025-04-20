package io.jenkins.plugins.jobcacher.oras;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import land.oras.Config;
import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.LocalPath;
import land.oras.Manifest;
import land.oras.Registry;
import land.oras.exception.OrasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry client wrapping the {@link Registry} instance and its configuration.
 */
public class RegistryClient {

    /**
     * Logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(RegistryClient.class);

    private final RegistryConfig config;
    private final Registry registry;

    /**
     * Create a new registry client.
     * @param registryUrl The URL of the registry
     * @param namespace The namespace to use
     * @param credentials The credentials to use
     */
    public RegistryClient(
            @NonNull String registryUrl, @NonNull String namespace, @NonNull UsernamePasswordCredentials credentials) {
        this.config = new RegistryConfig(registryUrl, namespace, credentials);
        this.registry = buildRegistry();
    }

    /**
     * Create a new registry client.
     * @param config The configuration to use
     */
    public RegistryClient(@NonNull RegistryConfig config) {
        this(config.registryUrl(), config.namespace(), config.credentials());
    }

    /**
     * Get the configuration of the registry.
     * @return The configuration
     */
    public RegistryConfig getConfig() {
        return config;
    }

    /**
     * Check if an artifact exists in the registry.
     * @param fullName The full name of the artifact
     * @param path The path of the artifact
     * @return {@code true} if the artifact exists, {@code false} otherwise
     */
    public boolean exists(String fullName, String path) {
        ContainerRef ref = buildRef(fullName, path);
        try {
            boolean exists = registry.getTags(ref).tags().contains("latest");
            if (exists) {
                Manifest manifest = registry.getManifest(ref);
                LOG.debug(
                        "Artifact with full name {} and path {} exists in registry at digest {}",
                        fullName,
                        path,
                        manifest.getDescriptor().getDigest());
            }
            return exists;
        } catch (OrasException e) {
            LOG.debug("Artifact with full name {} and path {} doesn't exists: {}", fullName, path, e.getMessage());
            return false;
        }
    }

    /**
     * Delete an artifact from the registry.
     * @param path The path of the artifact
     */
    public void delete(String fullName, String path) {
        registry.deleteManifest(buildRef(fullName, path));
    }

    /**
     * Upload an artifact to the registry.
     * @param fullName The full name of the artifact
     * @param path The path of the artifact
     * @param target The path of the artifact to upload
     * @throws Exception If an error occurs
     */
    public void download(String fullName, String path, Path target) throws Exception {
        ContainerRef ref = buildRef(fullName, path);
        Manifest manifest = registry.getManifest(ref);
        try (InputStream is =
                registry.fetchBlob(ref.withDigest(manifest.getLayers().get(0).getDigest()))) {
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Upload an artifact to the registry.
     * @param fullName The full name of the artifact
     * @param path The path of the artifact
     * @param source The path of the artifact to upload
     * @throws Exception If an error occurs
     */
    public void upload(String fullName, String path, Path source) throws Exception {
        ContainerRef ref = buildRef(fullName, path);
        registry.pushArtifact(ref, LocalPath.of(source));
    }

    private ContainerRef buildRef(String fullName, String path) {
        return ContainerRef.parse("%s/%s:latest".formatted(fullName, path)).forRegistry(registry);
    }

    /**
     * Test connection to the registry but uploading an artifact and deleting it.
     */
    public void testConnection() throws Exception {
        Path tmpFile = Files.createTempFile("tmp-", "jenkins-oras-plugin-test");
        Files.writeString(tmpFile, "jenkins-oras-plugin-test");
        ContainerRef ref = ContainerRef.parse("%s/jenkins-oras-plugin-test:latest".formatted(config.namespace()))
                .forRegistry(config.registryUrl);
        Layer layer = registry.pushBlob(ref, tmpFile);
        registry.pushConfig(ref, Config.empty());
        Manifest manifest = registry.pushManifest(ref, Manifest.empty().withLayers(List.of(layer)));
        registry.deleteManifest(ref.withDigest(manifest.getDescriptor().getDigest()));
    }

    private Registry buildRegistry() {
        Registry.Builder builder = Registry.builder();
        if (config.credentials == null) {
            return builder.insecure().withRegistry(config.registryUrl).build(); // TODO: Insecure option
        }
        return builder.defaults(
                        config.credentials.getUsername(),
                        config.credentials.getPassword().getPlainText())
                .withRegistry(config.registryUrl)
                .build();
    }

    /**
     * Configuration of the registry.
     */
    public record RegistryConfig(String registryUrl, String namespace, UsernamePasswordCredentials credentials)
            implements Serializable {}
}
