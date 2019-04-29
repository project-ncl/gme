package org.jboss.gm.analyzer.alignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.ProjectVersionFactory.withGAV;

import java.util.Arrays;

import org.aeonbits.owner.ConfigFactory;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectRef;
import org.jboss.gm.common.Configuration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;

public class DependencyExclusionCustomizerFromConfigurationAndModuleTest {

    private static final ProjectRef PROJECT = new SimpleProjectRef("org.acme", "test");

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void noDependencyExclusionProperty() {
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration,
                PROJECT);

        assertThat(sut).isSameAs(AlignmentService.RequestCustomizer.NOOP);
    }

    @Test
    public void erroneousPropertiesDontCauseFailure() {
        System.setProperty("dependencyExclusion.org.acme", "");

        final Configuration configuration = ConfigFactory.create(Configuration.class);
        final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration,
                PROJECT);

        assertThat(sut).isSameAs(AlignmentService.RequestCustomizer.NOOP);
    }

    @Test
    public void ensureExclusionMatches() {
        final ProjectVersionRef project = withGAV("org.acme", "dummy", "1.0.0");
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef hibernateValidatorGav = withGAV("org.hibernate", "hibernate-validator",
                "6.0.16.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        final ProjectVersionRef jacksonDatabindGav = withGAV("com.fasterxml.jackson.core", "jackson-databind",
                "2.9.8");
        final ProjectVersionRef mongoGav = withGAV("org.mongodb", "mongo-java-driver", "3.10.2");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");
        final ProjectVersionRef wiremockGav = withGAV("com.github.tomakehurst", "wiremock-jre8", "2.23.2");

        System.setProperty("dependencyExclusion.org.hibernate:*@*",
                ""); // should result in the removal of all our hibernate deps
        System.setProperty("dependencyExclusion.com.fasterxml.jackson.core:jackson-databind@*",
                ""); // should result in the removal of our jackson dependency
        System.setProperty("dependencyExclusion.io.undertow:undertow-servlet@*",
                ""); // should NOT result in the removal of our undertow dependency since the artifact doesn't match
        System.setProperty("dependencyExclusion.org.mockito:*@org.acme:test",
                ""); // should result in the removal of our mockito dependency
        System.setProperty("dependencyExclusion.com.github.tomakehurst:*@org.acme:other",
                ""); // should NOT result in the removal of our wiremock dependency since the module doesn't match

        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final AlignmentService.RequestCustomizer sut = DependencyExclusionCustomizer.fromConfigurationForModule(configuration,
                PROJECT);

        final AlignmentService.Request originalReq = new AlignmentService.Request(project,
                Arrays.asList(hibernateGav, hibernateValidatorGav, undertowGav, jacksonDatabindGav, mongoGav, mockitoGav,
                        wiremockGav));
        final AlignmentService.Request customizedReq = sut.customize(originalReq);

        assertThat(customizedReq).isNotNull().satisfies(r -> {
            assertThat(r.getProject()).isEqualTo(project);
            assertThat(r.getDependencies()).extracting("artifactId").containsOnly("undertow-core", "mongo-java-driver",
                    "wiremock-jre8");
        });
    }
}
