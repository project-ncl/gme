package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;

public class DAAlignmentService implements AlignmentService {

	private final DefaultTranslator restEndpoint;

	public DAAlignmentService(String endpointUrl) {
		//TODO: the parameters needs to be verified
		restEndpoint = new DefaultTranslator(
				endpointUrl,
				Translator.RestProtocol.CURRENT,
				0,
				DefaultTranslator.CHUNK_SPLIT_COUNT,
				"",
				""
		);
	}

	@Override
	public Response align(Request request) {
		final List<ProjectVersionRef> translateRequest = new ArrayList<>(request.getDependencies().size() + 1);
		final ProjectVersionRef refOfProject = toProjectVersionRef(request.getProject());
		translateRequest.add(refOfProject);
		request.getDependencies().forEach(DAAlignmentService::toProjectVersionRef);

		final Map<ProjectVersionRef, String> translationMap = restEndpoint
				.translateVersions(translateRequest);

		return new Response(refOfProject, translationMap);
	}

	private static ProjectVersionRef toProjectVersionRef(GAV gav) {
		return new SimpleProjectVersionRef(new SimpleProjectVersionRef(gav.getGroup(), gav.getName(), gav.getVersion()));
	}

	private static class Response implements AlignmentService.Response {

		private final ProjectVersionRef refOfProject;
		private final Map<ProjectVersionRef, String>  translationMap;

		Response(ProjectVersionRef refOfProject, Map<ProjectVersionRef, String> translationMap) {
			this.refOfProject = refOfProject;
			this.translationMap = translationMap;
		}

		@Override
		public String getNewProjectVersion() {
			return translationMap.get(refOfProject);
		}

		@Override
		public String getAlignedVersionOfGav(GAV gav) {
			return translationMap.get(toProjectVersionRef(gav));
		}
	}
}
