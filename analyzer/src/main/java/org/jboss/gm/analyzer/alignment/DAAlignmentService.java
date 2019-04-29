package org.jboss.gm.analyzer.alignment;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link org.jboss.gm.analyzer.alignment.AlignmentService} that uses the Dependency Analyzer service
 * in order to get the proper aligned versions of dependencies (as well as the version of the project itself)
 *
 * The heavy lifting is done by {@link org.commonjava.maven.ext.io.rest.DefaultTranslator}
 */
public class DAAlignmentService implements AlignmentService {

    private final Translator restEndpoint;

    public DAAlignmentService(String endpointUrl) {
        //TODO: the parameters needs to be verified
        restEndpoint = new DefaultTranslator(
                endpointUrl,
                Translator.RestProtocol.CURRENT,
                0,
                DefaultTranslator.CHUNK_SPLIT_COUNT,
                "",
                "");
    }

    @Override
    public Response align(Request request) {
        final List<ProjectVersionRef> translateRequest = new ArrayList<>(request.getDependencies().size() + 1);
        final ProjectVersionRef refOfProject = request.getProject();
        translateRequest.add(refOfProject);
        translateRequest.addAll(request.getDependencies());

        final Map<ProjectVersionRef, String> translationMap = restEndpoint.translateVersions(translateRequest);

        return new Response(refOfProject, translationMap);
    }

    private static class Response implements AlignmentService.Response {

        private final ProjectVersionRef refOfProject;
        private final Map<ProjectVersionRef, String> translationMap;

        Response(ProjectVersionRef refOfProject, Map<ProjectVersionRef, String> translationMap) {
            this.refOfProject = refOfProject;
            this.translationMap = translationMap;
        }

        @Override
        public String getNewProjectVersion() {
            return translationMap.get(refOfProject);
        }

        @Override
        public String getAlignedVersionOfGav(ProjectVersionRef gav) {
            return translationMap.get(gav);
        }
    }
}
