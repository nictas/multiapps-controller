package com.sap.cloud.lm.sl.cf.core.util;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;

import com.sap.cloud.lm.sl.cf.core.cf.CloudControllerClientProvider;
import com.sap.cloud.lm.sl.cf.core.helpers.ClientHelper;
import com.sap.cloud.lm.sl.cf.core.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;

@Named
public class SpaceIdRetriever {

    private static final String SPACE_CACHE_SEPARATOR = "|";

    private final CloudControllerClientProvider clientProvider;
    // FIXME: Nothing is ever removed from this cache.
    private final Map<String, String> processSpaceCache = new HashMap<>();

    @Inject
    public SpaceIdRetriever(CloudControllerClientProvider clientProvider) {
        this.clientProvider = clientProvider;
    }

    public String getSpaceIdForProcess(UserInfo userInfo, String orgName, String spaceName, String processId) {
        String spaceCacheKey = getSpaceCacheKey(orgName, spaceName, processId);
        String spaceId = processSpaceCache.get(spaceCacheKey);
        if (spaceId == null) {
            spaceId = getSpaceId(userInfo, orgName, spaceName);
            if (processId != null) {
                processSpaceCache.put(spaceCacheKey, spaceId);
            }
        }
        return spaceId;
    }

    public String getSpaceId(UserInfo userInfo, String orgName, String spaceName) {
        CloudControllerClient client = clientProvider.getControllerClient(userInfo.getName());
        String spaceId = new ClientHelper(client).computeSpaceId(orgName, spaceName);
        if (spaceId == null) {
            throw new SLException(Messages.COULD_NOT_COMPUTE_SPACE_ID, orgName, spaceName);
        }
        return spaceId;
    }

    private String getSpaceCacheKey(String orgName, String spaceName, String processId) {
        return orgName + SPACE_CACHE_SEPARATOR + spaceName + SPACE_CACHE_SEPARATOR + processId;
    }

}
