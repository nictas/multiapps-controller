package com.sap.cloud.lm.sl.cf.core.helpers.v2;

import static com.sap.cloud.lm.sl.cf.core.util.ConfigurationEntriesUtil.PROVIDER_NID;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getOptionalParameter;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.getRequiredParameter;
import static com.sap.cloud.lm.sl.mta.util.PropertiesUtil.mergeProperties;

import java.util.Map;

import com.sap.cloud.lm.sl.cf.core.model.CloudTarget;
import com.sap.cloud.lm.sl.cf.core.model.ConfigurationFilter;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.core.util.ConfigurationEntriesUtil;
import com.sap.cloud.lm.sl.mta.builders.v2.PropertiesChainBuilder;
import com.sap.cloud.lm.sl.mta.model.Resource;

public class ConfigurationFilterParser {

    private static final String NEW_SYNTAX_FILTER = "configuration";
    private static final String OLD_SYNTAX_FILTER = "mta-provides-dependency";

    protected final CloudTarget currentTarget;
    protected final PropertiesChainBuilder chainBuilder;
    protected final String targetedNamespace;

    public ConfigurationFilterParser(CloudTarget currentTarget, PropertiesChainBuilder chainBuilder, String namespace) {
        this.currentTarget = currentTarget;
        this.chainBuilder = chainBuilder;
        this.targetedNamespace = namespace;
    }

    public ConfigurationFilter parse(Resource resource) {
        String type = getType(resource);
        if (OLD_SYNTAX_FILTER.equals(type)) {
            return parseOldSyntaxFilter(resource);
        }
        if (NEW_SYNTAX_FILTER.equals(type)) {
            return parseNewSyntaxFilter(resource);
        }
        return null;
    }

    private String getType(Resource resource) {
        Map<String, Object> mergedParameters = mergeProperties(chainBuilder.buildResourceChain(resource.getName()));
        return (String) mergedParameters.get(SupportedParameters.TYPE);
    }

    private ConfigurationFilter parseOldSyntaxFilter(Resource resource) {
        Map<String, Object> parameters = getParameters(resource);
        String mtaId = getRequiredParameter(parameters, SupportedParameters.MTA_ID);
        String mtaProvidesDependency = getRequiredParameter(parameters, SupportedParameters.MTA_PROVIDES_DEPENDENCY);
        String mtaVersion = getRequiredParameter(parameters, SupportedParameters.MTA_VERSION);
        String providerId = ConfigurationEntriesUtil.computeProviderId(mtaId, mtaProvidesDependency);
        return new ConfigurationFilter(PROVIDER_NID, providerId, mtaVersion, null, currentTarget, null);
    }

    private ConfigurationFilter parseNewSyntaxFilter(Resource resource) {
        Map<String, Object> parameters = getParameters(resource);
        String version = getOptionalParameter(parameters, SupportedParameters.VERSION);
        String nid = getOptionalParameter(parameters, SupportedParameters.PROVIDER_NID);
        String namespace = getEffectiveNamespace(parameters);
        String pid = getOptionalParameter(parameters, SupportedParameters.PROVIDER_ID);
        Map<String, Object> filter = getOptionalParameter(parameters, SupportedParameters.FILTER);
        Map<String, Object> target = getOptionalParameter(parameters, SupportedParameters.TARGET);
        boolean hasExplicitTarget = target != null;
        CloudTarget cloudTarget = hasExplicitTarget ? parseSpaceTarget(target) : currentTarget;
        return new ConfigurationFilter(nid, pid, version, namespace, cloudTarget, filter, hasExplicitTarget);
    }

    private CloudTarget parseSpaceTarget(Map<String, Object> target) {
        String organizationName = getRequiredParameter(target, SupportedParameters.ORGANIZATION_NAME);
        String spaceName = getRequiredParameter(target, SupportedParameters.SPACE_NAME);
        return new CloudTarget(organizationName, spaceName);
    }

    public Map<String, Object> getParameters(Resource resource) {
        return resource.getParameters();
    }

    public String getEffectiveNamespace(Map<String, Object> filterParameters) {
        String filterNamespace = getOptionalParameter(filterParameters, SupportedParameters.PROVIDER_NAMESPACE);
        if (filterNamespace != null) {
            return filterNamespace;
        }

        return this.targetedNamespace;
    }
}
