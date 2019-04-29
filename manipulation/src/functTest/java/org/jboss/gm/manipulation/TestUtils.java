package org.jboss.gm.manipulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.assertj.core.groups.Tuple;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.gm.common.alignment.ManipulationModel;

public final class TestUtils {

    private TestUtils() {
    }

    public static void copyDirectory(String classpathResource, File target) throws URISyntaxException, IOException {
        FileUtils.copyDirectory(Paths
                .get(TestUtils.class.getClassLoader().getResource(classpathResource).toURI()).toFile(), target);
    }

    static Tuple getAlignedTuple(ManipulationModel alignment, String artifactId, String expected) {
        final List<String> versions = alignment.getAlignedDependencies().entrySet().stream()
                .filter(entry -> entry.getKey().contains(artifactId))
                .map(entry -> entry.getValue().getVersionString())
                .collect(Collectors.toList());
        final int count = versions.size();
        if (count != 1) {
            if (count == 0) {
                if (expected == null) {
                    throw new IllegalArgumentException("No alignment was found for " + artifactId
                            + " and needed an expected value to test against");
                }
                return tuple(artifactId, expected);
            }
            throw new IllegalArgumentException("Cannot retrieve a single version from " + artifactId
                    + ". Use more specific information");
        }

        return tuple(artifactId, versions.get(0));
    }

    static Tuple getAlignedTuple(ManipulationModel alignment, String artifactId) {
        return getAlignedTuple(alignment, artifactId, null);
    }

    static Pair<Model, ManipulationModel> getModelAndCheckGAV(File parentLocationForPom, ManipulationModel alignment,
            String relativeGeneratedPomPathAsString)
            throws IOException,
            XmlPullParserException {

        return getModelAndCheckGAV(parentLocationForPom, alignment, relativeGeneratedPomPathAsString, false);
    }

    static Pair<Model, ManipulationModel> getModelAndCheckGAV(File parentLocationForPom, ManipulationModel alignment,
            String relativeGeneratedPomPathAsString, boolean external)
            throws IOException,
            XmlPullParserException {
        final Path generatedPomPath = parentLocationForPom.toPath().resolve(relativeGeneratedPomPathAsString);
        assertThat(generatedPomPath).isRegularFile();

        // find module
        final ManipulationModel module;
        if (!external && !relativeGeneratedPomPathAsString.startsWith("build")) {
            final String moduleName = relativeGeneratedPomPathAsString.substring(0,
                    relativeGeneratedPomPathAsString.indexOf('/'));
            module = alignment.getChildren().get(moduleName);
        } else {
            module = alignment;
        }

        final MavenXpp3Reader reader = new MavenXpp3Reader();
        final Model model = reader.read(new FileReader(generatedPomPath.toFile()));
        assertThat(model.getGroupId()).isEqualTo(alignment.getGroup());
        assertThat(model.getArtifactId()).isEqualTo(module.getName());
        assertThat(model.getVersion()).isEqualTo(module.getVersion());

        return new ImmutablePair<>(model, module);
    }
}
