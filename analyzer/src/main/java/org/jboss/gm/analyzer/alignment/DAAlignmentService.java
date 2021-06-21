package org.jboss.gm.analyzer.alignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.io.rest.RestException;
import org.commonjava.maven.ext.io.rest.Translator;
import org.jboss.gm.common.Configuration;
import org.jboss.gm.common.logging.GMLogger;
import org.jboss.gm.common.utils.RESTUtils;
import org.slf4j.Logger;

import static org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence.NONE;

/**
 * An implementation of {@link AlignmentService} that uses the Dependency Analyzer service
 * in order to get the proper aligned versions of dependencies (as well as the version of the project itself)
 *
 * The heavy lifting is done by {@link org.commonjava.maven.ext.io.rest.DefaultTranslator}
 */
public class DAAlignmentService implements AlignmentService {

    private final Logger logger = GMLogger.getLogger(getClass());

    private final Translator restEndpoint;

    private final DependencyState.DependencyPrecedence dependencySource;

    public DAAlignmentService(Configuration configuration) {
        dependencySource = configuration.dependencyConfiguration();

        final String endpointUrl = configuration.daEndpoint();

        if (endpointUrl == null && dependencySource != NONE) {
            throw new ManipulationUncheckedException("'{}' must be configured in order for dependency scanning to work",
                    Configuration.DA);
        }

        restEndpoint = RESTUtils.getTranslator(configuration);
    }

    @Override
    public Response align(AlignmentService.Request request) throws RestException {
        if (dependencySource == NONE) {
            logger.warn("No dependencySource configured ; unable to call endpoint");
            return new Response(Collections.emptyMap());
        }

        final List<ProjectVersionRef> vParams = request.getDependencies();

        logger.debug("Passing {} GAVs into the REST client api {}", vParams.size(), vParams);

        final Map<ProjectVersionRef, String> vMap = restEndpoint.lookupVersions(vParams);

        logger.info("REST Client returned: {}", vMap);

        final Response response = new Response(vMap);

        final List<ProjectVersionRef> pParams = request.getProject();

        if (!pParams.isEmpty()) {
            logger.debug("Passing {} project GAVs into the REST client api {}", pParams.size(), pParams);

            final Map<ProjectVersionRef, String> pMap = restEndpoint.lookupProjectVersions(pParams);

            logger.info("REST Client returned for project versions: {}", pMap);

            final ProjectVersionRef projectVersion = pParams.get(0);
            final String newProjectVersion = pMap.get(projectVersion);

            logger.info("Retrieving project version {} and returning {}", projectVersion, newProjectVersion);

            response.getTranslationMap().putAll(pMap);
            response.setNewProjectVersion(newProjectVersion);
        }

        return response;
    }
}
