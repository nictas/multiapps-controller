package com.sap.cloud.lm.sl.cf.process.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.cloudfoundry.client.lib.domain.UploadToken;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.io.ByteStreams;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudServiceExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.ImmutableCloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.ImmutableCloudServiceExtended;
import com.sap.cloud.lm.sl.cf.core.model.Phase;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.mock.MockDelegateExecution;
import com.sap.cloud.lm.sl.cf.process.util.StepLogger;
import com.sap.cloud.lm.sl.common.SLException;

public class StepsUtilTest {

    private static final String EXAMPLE_USER = "exampleUser";
    private static final String EXAMPLE_MODULE_NAME = "exampleModule";

    protected final DelegateExecution context = MockDelegateExecution.createSpyInstance();

    @Test
    public void testDetermineCurrentUserWithSetUser() {
        Mockito.when(context.getVariable(Mockito.eq(Constants.VAR_USER)))
               .thenReturn(EXAMPLE_USER);
        String determinedUser = StepsUtil.determineCurrentUser(context, Mockito.mock(StepLogger.class));
        assertEquals(EXAMPLE_USER, determinedUser);
    }

    public void testDetermineCurrentUserError() {
        Assertions.assertThrows(SLException.class, () -> StepsUtil.determineCurrentUser(context, Mockito.mock(StepLogger.class)));
    }

    @Test
    public void testGetModuleContentAsStream() throws Exception {
        byte[] bytes = "example byte array".getBytes();
        Mockito.when(context.getVariable(Mockito.eq(constructModuleContentVariable(EXAMPLE_MODULE_NAME))))
               .thenReturn(bytes);
        InputStream stream = StepsUtil.getModuleContentAsStream(context, EXAMPLE_MODULE_NAME);
        byte[] readBytes = ByteStreams.toByteArray(stream);
        assertByteArraysMatch(bytes, readBytes);
    }

    @Test
    public void testGetModuleContentAsStreamNotFound() {
        Mockito.when(context.getVariable(Mockito.eq(constructModuleContentVariable(EXAMPLE_MODULE_NAME))))
               .thenReturn(null);
        Assertions.assertThrows(SLException.class, () -> StepsUtil.getModuleContentAsStream(context, EXAMPLE_MODULE_NAME));
    }

    private String constructModuleContentVariable(String moduleName) {
        return Constants.VAR_MTA_MODULE_CONTENT_PREFIX + moduleName;
    }

    private void assertByteArraysMatch(byte[] expected, byte[] actual) {
        Assertions.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assertions.assertEquals(expected[i], actual[i]);
        }
    }

    @Test
    public void testGetServicesToCreateWithCredentials() {
        CloudServiceExtended service = ImmutableCloudServiceExtended.builder()
                                                                    .name("my-service")
                                                                    .putCredential("integer-value", 1)
                                                                    .putCredential("double-value", 1.4)
                                                                    .putCredential("string-value", "1")
                                                                    .build();

        StepsUtil.setServicesToCreate(context, Collections.singletonList(service));
        List<CloudServiceExtended> actualServicesToCreate = StepsUtil.getServicesToCreate(context);

        assertEquals(1, actualServicesToCreate.size());
        assertFalse(actualServicesToCreate.get(0)
                                          .getCredentials()
                                          .isEmpty());
        assertEquals(Integer.class, actualServicesToCreate.get(0)
                                                          .getCredentials()
                                                          .get("integer-value")
                                                          .getClass());
        assertEquals(Double.class, actualServicesToCreate.get(0)
                                                         .getCredentials()
                                                         .get("double-value")
                                                         .getClass());
        assertEquals(String.class, actualServicesToCreate.get(0)
                                                         .getCredentials()
                                                         .get("string-value")
                                                         .getClass());
    }

    @Test
    public void testGetAppsToDeployWithBindingParameters() {
        Map<String, Map<String, Object>> bindingParameters = new HashMap<>();
        Map<String, Object> serviceBindingParameters = new HashMap<>();
        serviceBindingParameters.put("integer-value", 1);
        serviceBindingParameters.put("double-value", 1.4);
        serviceBindingParameters.put("string-value", "1");
        bindingParameters.put("service-1", serviceBindingParameters);

        CloudApplicationExtended application = ImmutableCloudApplicationExtended.builder()
                                                                                .name("my-application")
                                                                                .bindingParameters(bindingParameters)
                                                                                .build();

        StepsUtil.setApp(context, application);
        CloudApplicationExtended actualAppToDeploy = StepsUtil.getApp(context);

        assertFalse(actualAppToDeploy.getBindingParameters()
                                     .isEmpty());
        assertFalse(actualAppToDeploy.getBindingParameters()
                                     .get("service-1")
                                     .isEmpty());
        assertEquals(Integer.class, actualAppToDeploy.getBindingParameters()
                                                     .get("service-1")
                                                     .get("integer-value")
                                                     .getClass());
        assertEquals(Double.class, actualAppToDeploy.getBindingParameters()
                                                    .get("service-1")
                                                    .get("double-value")
                                                    .getClass());
        assertEquals(String.class, actualAppToDeploy.getBindingParameters()
                                                    .get("service-1")
                                                    .get("string-value")
                                                    .getClass());
    }

    @Test
    public void testSetAndGetUploadToken() {
        UploadToken expectedUploadToken = new UploadToken();
        expectedUploadToken.setPackageGuid(UUID.fromString("ab0703c2-1a50-11e9-ab14-d663bd873d93"));
        expectedUploadToken.setToken("token");

        StepsUtil.setUploadToken(expectedUploadToken, context);
        UploadToken actualUploadToken = StepsUtil.getUploadToken(context);

        assertEquals(expectedUploadToken.getToken(), actualUploadToken.getToken());
        assertEquals(expectedUploadToken.getPackageGuid(), actualUploadToken.getPackageGuid());
    }

    @Test
    public void testSetAndGetPhase() {
        Phase expectedPhase = Phase.UNDEPLOY;
        StepsUtil.setPhase(context, expectedPhase);
        Phase actualPhase = Phase.valueOf((String) context.getVariable(Constants.VAR_PHASE));

        assertEquals(expectedPhase, actualPhase);
    }

    @Test
    public void testShouldVerifyArchiveSignatureSet() {
        Mockito.when(context.getVariable(Constants.PARAM_VERIFY_ARCHIVE_SIGNATURE))
               .thenReturn(true);
        Assertions.assertTrue(StepsUtil.shouldVerifyArchiveSignature(context));
    }

}
