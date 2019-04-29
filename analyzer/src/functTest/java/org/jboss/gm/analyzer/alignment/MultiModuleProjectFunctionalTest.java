package org.jboss.gm.analyzer.alignment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.alignment.ManipulationModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

public class MultiModuleProjectFunctionalTest extends AbstractWiremockTest {

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Before
    public void setup() {
        final String project_root_called = "project root called";
        final String first_dependency_called = "first module called";
        final String second_dependency_called = "second module called";

        stubDACall(STARTED, "root", project_root_called);
        stubDACall(project_root_called, "subproject1", first_dependency_called);
        stubDACall(first_dependency_called, "subproject2", second_dependency_called);
        stubDACall(second_dependency_called, "subproject11", "foo");

        System.setProperty(Configuration.DA, "http://127.0.0.1:" + AbstractWiremockTest.PORT + "/da/rest/v-1");
    }

    private void stubDACall(String initialState, String projectName, String nextState) {
        stubFor(post(urlEqualTo("/da/rest/v-1/reports/lookup/gavs"))
                .inScenario("multi-module")
                .whenScenarioStateIs(initialState)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse("multi-module-da-" + projectName + ".json")))
                .willSetStateTo(nextState));
    }

    @Test
    public void ensureAlignmentFileCreatedAndAlignmentTaskRun() throws IOException, URISyntaxException {
        final ManipulationModel alignmentModel = TestUtils.align(tempDir, "multi-module");

        assertThat(alignmentModel).isNotNull().satisfies(am -> {
            assertThat(am.getGroup()).isEqualTo("org.acme");
            assertThat(am.getName()).isEqualTo("root");

            assertThat(am.getChildren().keySet()).hasSize(2).containsExactly("subproject1", "subproject2");

            assertThat(am.findCorrespondingChild("root")).satisfies(root -> {
                assertThat(root.getVersion()).isEqualTo("1.1.2.redhat-00004");
                assertThat(root.getAlignedDependencies()).isEmpty();
            });

            assertThat(am.findCorrespondingChild("subproject1")).satisfies(subproject1 -> {
                assertThat(subproject1.getChildren().keySet()).hasSize(1).containsExactly("subproject11");
                assertThat(subproject1.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject1.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"),
                                tuple("hibernate-core", "5.4.2.Final-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild("subproject2")).satisfies(subproject2 -> {
                assertThat(subproject2.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject2.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("resteasy-jaxrs", "3.6.3.SP1-redhat-00001"));
            });

            assertThat(am.findCorrespondingChild(":subproject1:subproject11")).satisfies(subproject11 -> {
                assertThat(subproject11.getVersion()).isEqualTo("1.1.2.redhat-00004");
                final Collection<ProjectVersionRef> alignedDependencies = subproject11.getAlignedDependencies().values();
                assertThat(alignedDependencies)
                        .extracting("artifactId", "versionString")
                        .containsOnly(
                                tuple("spring-context", "5.1.6.RELEASE-redhat-00005"));
            });
        });
    }

}
