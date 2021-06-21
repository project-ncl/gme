package org.jboss.gm.analyzer.alignment;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.RestException;
import org.jboss.gm.common.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.gm.common.versioning.ProjectVersionFactory.withGAV;

public class DAAlignmentServiceWiremockTest {

    private static final int PORT = 8089;

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_GAVS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAResponse())));
        stubFor(post(urlEqualTo("/da/rest/v-1/" + DefaultTranslator.Endpoint.LOOKUP_LATEST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(readSampleDAProjectResponse())));
    }

    @Test
    public void alignmentWorksAsExpected() throws RestException {
        System.setProperty(Configuration.DA, String.format("http://localhost:%d/da/rest/v-1", PORT));
        final Configuration configuration = ConfigFactory.create(Configuration.class);

        final DAAlignmentService sut = new DAAlignmentService(configuration);

        final ProjectVersionRef projectGav = withGAV("org.acme", "dummy", "1.0.0");
        final ProjectVersionRef hibernateGav = withGAV("org.hibernate", "hibernate-core", "5.3.7.Final");
        final ProjectVersionRef undertowGav = withGAV("io.undertow", "undertow-core", "2.0.15.Final");
        final ProjectVersionRef mockitoGav = withGAV("org.mockito", "mockito-core", "2.27.0");
        final AlignmentService.Response response = sut.align(new AlignmentService.Request(
                Collections.singletonList(projectGav),
                Stream.of(hibernateGav,
                        undertowGav,
                        mockitoGav).collect(Collectors.toList())));

        assertThat(response).isNotNull().satisfies(r -> {
            assertThat(r.getNewProjectVersion()).isNull();
            assertThat(r.getAlignedVersionOfGav(hibernateGav)).isEqualTo("5.3.7.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(undertowGav)).isEqualTo("2.0.15.Final-redhat-00001");
            assertThat(r.getAlignedVersionOfGav(mockitoGav)).isNull();
        });
    }

    private String readSampleDAResponse() throws URISyntaxException, IOException {
        return FileUtils.readFileToString(
                Paths.get(DAAlignmentServiceWiremockTest.class.getClassLoader().getResource("sample-da-response.json")
                        .toURI()).toFile(),
                StandardCharsets.UTF_8.name());
    }

    private String readSampleDAProjectResponse() throws URISyntaxException, IOException {
        return FileUtils.readFileToString(
                Paths.get(DAAlignmentServiceWiremockTest.class.getClassLoader().getResource("sample-da-response-project" +
                        ".json")
                        .toURI()).toFile(),
                StandardCharsets.UTF_8.name());
    }
}
