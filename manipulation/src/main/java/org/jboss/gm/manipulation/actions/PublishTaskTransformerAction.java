package org.jboss.gm.manipulation.actions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.model.ManipulationModel;
import org.jboss.gm.common.utils.ProjectUtils;
import org.jboss.gm.manipulation.ResolvedDependenciesRepository;

import static org.jboss.gm.manipulation.ManipulationPlugin.MAVEN_PUBLISH_PLUGIN;

/**
 * Fixes pom.xml generation in "maven-publish" plugin.
 * <p>
 * Applies PomTransformer, that overrides dependencies versions according to given configuration, to all maven
 * publications.
 */
public class PublishTaskTransformerAction
        implements Action<Project> {

    private final ManipulationModel alignmentConfiguration;
    private final ResolvedDependenciesRepository resolvedDependenciesRepository;

    private final Logger logger = GMLogger.getLogger(getClass());

    public PublishTaskTransformerAction(ManipulationModel alignmentConfiguration,
            ResolvedDependenciesRepository resolvedDependenciesRepository) {
        this.alignmentConfiguration = alignmentConfiguration;
        this.resolvedDependenciesRepository = resolvedDependenciesRepository;
    }

    @Override
    public void execute(Project project) {
        if (!project.getPluginManager().hasPlugin(MAVEN_PUBLISH_PLUGIN)) {
            return;
        }
        // GenerateMavenPom tasks need to be postponed until after compileJava task, because that's where artifact
        // resolution is normally triggered and ResolvedDependenciesRepository is filled. If GenerateMavenPom runs
        // before compileJava, we will see empty ResolvedDependenciesRepository here.
        project.getTasks().withType(GenerateMavenPom.class).all(task -> {
            if (project.getTasks().findByName("compileJava") != null) {
                task.dependsOn("compileJava");
            }
        });

        // If someone has done "generatePomFileForPluginMavenPublication.enabled" while adding
        // java-gradle-plugin rather than configuring the Gradle plugin with "automatedPublishing=false"
        // this can cause issues during publishing. Elasticsearch is one such culprit (fixed on 7.x codebase).
        project.getTasks().stream().filter(
                t -> t.getName().startsWith("generatePomFileFor")
                        && t.getName().endsWith("Publication"))
                .forEach(t -> {
                    String generateName = t.getName().replaceAll("generatePomFileFor([a-zA-Z]+)Publication", "$1");
                    if (!t.getEnabled() && project.getTasks().stream().anyMatch(tt -> tt.getName().contains(generateName))) {
                        logger.warn("A '{}' (full name: '{}') publication has been added but the POM file generation disabled. "
                                + "This causes issues with Gradle and should be reviewed. Force enabling it to prevent future errors.",
                                generateName, t.getName());
                        t.setEnabled(true);
                    }
                });

        project.getExtensions().getByType(PublishingExtension.class).getPublications()
                .withType(MavenPublication.class)
                .configureEach(publication -> {
                    logger.debug("Applying POM transformer to publication " + publication.getName());

                    final String archivesBaseName = ProjectUtils.getArchivesBaseName(project);
                    if (archivesBaseName != null && !publication.getArtifactId().equals(archivesBaseName)) {
                        logger.warn(
                                "Updating publication artifactId ({}) as it is not consistent with archivesBaseName ({})",
                                publication.getArtifactId(), archivesBaseName);
                        publication.setArtifactId(archivesBaseName);
                    }

                    if (!project.getVersion().equals(publication.getVersion())) {
                        logger.warn(
                                "Mismatch between project version ({}) and publication version ({}). Resetting to project version.",
                                project.getVersion(), publication.getVersion());
                        publication.setVersion(project.getVersion().toString());
                    }
                    if (publication.getPom() != null) {
                        publication.getPom()
                                .withXml(new MavenPomTransformerAction(alignmentConfiguration,
                                        resolvedDependenciesRepository));
                    }
                });
    }
}
