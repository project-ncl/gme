package org.jboss.gm.analyzer.alignment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.lang3.StringUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.api.Project;
import org.jboss.gm.common.Configuration;

/**
 * This is what {@value org.jboss.gm.analyzer.alignment.AlignmentTask#NAME} task uses to retrieve a fully wired
 * {@link org.jboss.gm.analyzer.alignment.AlignmentService}
 */
public final class AlignmentServiceFactory {

    private AlignmentServiceFactory() {
    }

    public static AlignmentService getAlignmentService(Project project) throws ManipulationException {
        if (StringUtils.isEmpty(project.getVersion().toString())) {
            throw new ManipulationException("Project version is empty. Unable to continue.");
        }

        Configuration configuration = ConfigCache.getOrCreate(Configuration.class);

        final ProjectVersionRef projectVersionRef = new SimpleProjectVersionRef(project.getGroup().toString(),
                project.getName(), project.getVersion().toString());
        return new WithCustomizersDelegatingAlignmentService(new DAAlignmentService(configuration),
                getRequestCustomizers(configuration, projectVersionRef),
                getResponseCustomizers(configuration, projectVersionRef));
    }

    private static List<AlignmentService.RequestCustomizer> getRequestCustomizers(Configuration configuration,
            ProjectVersionRef projectVersionRef) {
        return Collections
                .singletonList(DependencyExclusionCustomizer.fromConfigurationForModule(configuration, projectVersionRef));
    }

    private static List<AlignmentService.ResponseCustomizer> getResponseCustomizers(Configuration configuration,
            ProjectVersionRef projectVersionRef) {
        return Arrays.asList(DependencyOverrideCustomizer.fromConfigurationForModule(configuration, projectVersionRef),
                new UpdateProjectVersionCustomizer(projectVersionRef, configuration));
    }
}