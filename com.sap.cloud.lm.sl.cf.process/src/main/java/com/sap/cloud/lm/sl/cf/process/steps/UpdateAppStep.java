package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudServiceBinding;
import org.cloudfoundry.client.lib.domain.Staging;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.cloud.lm.sl.cf.client.lib.domain.CloudApplicationExtended;
import com.sap.cloud.lm.sl.cf.client.lib.domain.StagingExtended;
import com.sap.cloud.lm.sl.cf.core.cf.PlatformType;
import com.sap.cloud.lm.sl.cf.core.cf.services.ServiceOperationType;
import com.sap.cloud.lm.sl.cf.core.util.UriUtil;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.common.util.JsonUtil;
import com.sap.cloud.lm.sl.persistence.services.FileStorageException;
import com.sap.cloud.lm.sl.slp.model.StepMetadata;

@Component("updateAppStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateAppStep extends CreateAppStep {

    public static StepMetadata getMetadata() {
        return StepMetadata.builder().id("updateAppTask").displayName("Update App").description("Update App").build();
    }

    @Override
    protected ExecutionStatus executeStepInternal(DelegateExecution context) throws SLException, FileStorageException {
        getStepLogger().logActivitiTask();

        // Get the next cloud application from the context
        CloudApplicationExtended app = StepsUtil.getApp(context);

        // Get the existing application from the context
        CloudApplication existingApp = StepsUtil.getExistingApp(context);

        try {
            getStepLogger().info(Messages.UPDATING_APP, app.getName());

            // Get a cloud foundry client
            CloudFoundryOperations client = getCloudFoundryClient(context);

            // Get application parameters
            String appName = app.getName();
            StagingExtended staging = app.getStaging();
            Integer diskQuota = (app.getDiskQuota() != 0) ? app.getDiskQuota() : null;
            Integer memory = (app.getMemory() != 0) ? app.getMemory() : null;
            List<String> uris = app.getUris();
            Map<String, String> env = app.getEnvAsMap();

            boolean appPropertiesChanged = false;

            // Update the application
            if (hasChanged(staging, existingApp.getStaging())) {
                getStepLogger().debug("Updating staging of application \"{0}\"", appName);
                if (platformTypeSupplier.get() == PlatformType.CF) {
                    applicationStagingUpdater.updateApplicationStaging(client, appName, staging);
                } else {
                    client.updateApplicationStaging(appName, staging);
                }
                appPropertiesChanged = true;
            }
            if (memory != null && !memory.equals(existingApp.getMemory())) {
                getStepLogger().debug("Updating memory of application \"{0}\"", appName);
                client.updateApplicationMemory(appName, memory);
                appPropertiesChanged = true;
            }
            if (diskQuota != null && !diskQuota.equals(existingApp.getDiskQuota())) {
                getStepLogger().debug("Updating disk quota of application \"{0}\"", appName);
                client.updateApplicationDiskQuota(appName, diskQuota);
                appPropertiesChanged = true;
            }
            if (hasChanged(uris, existingApp.getUris())) {
                getStepLogger().debug("Updating uris of application \"{0}\"", appName);
                client.updateApplicationUris(appName, uris);
                appPropertiesChanged = true;
            }
            appPropertiesChanged = updateApplicationServices(app, existingApp, client, context) ? true : appPropertiesChanged;
            updateAppDigest(env, existingApp.getEnvAsMap());
            if (!env.equals(existingApp.getEnvAsMap())) {
                getStepLogger().debug("Updating env of application \"{0}\"", appName);
                client.updateApplicationEnv(appName, env);
                appPropertiesChanged = true;
            }

            if (!appPropertiesChanged) {
                getStepLogger().info(Messages.APPLICATION_UNCHANGED, app.getName());
            } else {
                getStepLogger().debug(Messages.APP_UPDATED, app.getName());
            }

            StepsUtil.setAppPropertiesChanged(context, appPropertiesChanged);

            return ExecutionStatus.SUCCESS;
        } catch (SLException e) {
            getStepLogger().error(e, Messages.ERROR_UPDATING_APP, app.getName());
            throw e;
        } catch (CloudFoundryException cfe) {
            SLException e = StepsUtil.createException(cfe);
            getStepLogger().error(e, Messages.ERROR_UPDATING_APP, app.getName());
            throw e;
        }
    }

    private void updateAppDigest(Map<String, String> newAppEnv, Map<String, String> existingAppEnv) {
        Object existingFileDigest = getExistingAppFileDigest(existingAppEnv);
        if (existingFileDigest == null) {
            return;
        }
        String newAppDeployAttributes = newAppEnv.get(com.sap.cloud.lm.sl.cf.core.Constants.ENV_DEPLOY_ATTRIBUTES);
        TreeMap<String, Object> newAppDeployAttributesMap = new TreeMap<>(JsonUtil.convertJsonToMap(newAppDeployAttributes));
        newAppDeployAttributesMap.put(com.sap.cloud.lm.sl.cf.core.Constants.ATTR_APP_CONTENT_DIGEST, existingFileDigest);
        newAppEnv.put(com.sap.cloud.lm.sl.cf.core.Constants.ENV_DEPLOY_ATTRIBUTES, JsonUtil.toJson(newAppDeployAttributesMap, true));
    }

    private Object getExistingAppFileDigest(Map<String, String> envAsMap) {
        String applicationDeployAttributes = envAsMap.get(com.sap.cloud.lm.sl.cf.core.Constants.ENV_DEPLOY_ATTRIBUTES);
        Map<String, Object> deployAttributesMap = JsonUtil.convertJsonToMap(applicationDeployAttributes);
        return deployAttributesMap.get(com.sap.cloud.lm.sl.cf.core.Constants.ATTR_APP_CONTENT_DIGEST);
    }

    private boolean updateApplicationServices(CloudApplicationExtended app, CloudApplication existingApp, CloudFoundryOperations client,
        DelegateExecution context) throws SLException, FileStorageException {
        boolean hasUnboundServices = unbindNotRequiredServices(existingApp, app.getServices(), client, context);
        List<String> services = app.getServices();
        Map<String, Map<String, Object>> bindingParameters = getBindingParameters(context, app);
        Set<String> updatedServices = getUpdatedServices(context);
        boolean hasUpdatedServices = updateServices(app, existingApp, bindingParameters, client, context, updatedServices, services);
        return hasUnboundServices || hasUpdatedServices;
    }

    private boolean unbindNotRequiredServices(CloudApplication app, List<String> requiredServices, CloudFoundryOperations client,
        DelegateExecution context) {
        boolean hasUnbindedService = false;
        for (String serviceName : app.getServices()) {
            if (!requiredServices.contains(serviceName)) {
                unbindService(app.getName(), serviceName, client, context);
                hasUnbindedService = true;
            }
        }
        return hasUnbindedService;
    }

    private void unbindService(String appName, String serviceName, CloudFoundryOperations client, DelegateExecution context) {
        getStepLogger().debug(Messages.UNBINDING_APP_FROM_SERVICE, appName, serviceName);
        client.unbindService(appName, serviceName);
    }

    private Set<String> getUpdatedServices(DelegateExecution context) {
        Map<String, ServiceOperationType> triggeredServiceOperations = StepsUtil.getTriggeredServiceOperations(context);
        Set<String> updatedServices = new HashSet<>();
        for (String serviceName : triggeredServiceOperations.keySet()) {
            if (triggeredServiceOperations.get(serviceName) == ServiceOperationType.UPDATE) {
                updatedServices.add(serviceName);
            }
        }
        return updatedServices;
    }

    private boolean updateServices(CloudApplicationExtended app, CloudApplication existingApp,
        Map<String, Map<String, Object>> bindingParameters, CloudFoundryOperations client, DelegateExecution context,
        Set<String> updatedServices, List<String> services) throws SLException {
        boolean hasUpdatedService = false;
        List<String> existingAppServices = existingApp.getServices();
        for (String serviceName : services) {
            Map<String, Object> bindingParametersForCurrentService = getBindingParametersForService(serviceName, bindingParameters);
            if (updatedServices.contains(serviceName)) {
                hasUpdatedService = true;
            }
            if (!existingAppServices.contains(serviceName)) {
                hasUpdatedService = true;
                bindService(context, client, app.getName(), serviceName, bindingParametersForCurrentService);
                continue;
            }
            List<CloudServiceBinding> existingServiceBindings = client.getServiceInstance(serviceName).getBindings();
            CloudServiceBinding existingBindingForApplication = getServiceBindingsForApplication(existingApp, existingServiceBindings);
            if (existingBindingForApplication == null) {
                hasUpdatedService = true;
                bindService(context, client, app.getName(), serviceName, bindingParametersForCurrentService);
                continue;
            }
            Map<String, Object> existingBindingParameters = existingBindingForApplication.getBindingOptions();
            if (!Objects.equals(existingBindingParameters, bindingParametersForCurrentService)) {
                unbindService(existingApp.getName(), serviceName, client, context);
                bindService(context, client, app.getName(), serviceName, bindingParametersForCurrentService);
                hasUpdatedService = true;
                continue;
            }
            getStepLogger().info(Messages.WILL_NOT_REBIND_APP_TO_SERVICE, serviceName, app.getName());
        }
        return hasUpdatedService;
    }

    protected CloudServiceBinding getServiceBindingsForApplication(CloudApplication existingApp,
        List<CloudServiceBinding> serviceBindings) {
        Optional<CloudServiceBinding> optCloudServiceBinding = serviceBindings.stream().filter(
            serviceBinding -> existingApp.getMeta().getGuid().equals(serviceBinding.getAppGuid())).findFirst();
        if (optCloudServiceBinding.isPresent()) {
            return optCloudServiceBinding.get();
        }
        return null;
    }

    private boolean hasChanged(StagingExtended staging, Staging existingStaging) {
        String buildpackUrl = staging.getBuildpackUrl();
        String command = staging.getCommand();
        String stack = staging.getStack();
        Integer healthCheckTimeout = staging.getHealthCheckTimeout();
        String healthCheckType = staging.getHealthCheckType();
        return (buildpackUrl != null && !buildpackUrl.equals(existingStaging.getBuildpackUrl()))
            || (command != null && !command.equals(existingStaging.getCommand()))
            || (stack != null && !stack.equals(existingStaging.getStack()))
            || (healthCheckTimeout != null && !healthCheckTimeout.equals(existingStaging.getHealthCheckTimeout()))
            || (healthCheckType != null);
    }

    private boolean hasChanged(List<String> uris, List<String> existingUris) {
        Set<String> urisSet = new HashSet<>(uris);
        Set<String> existingUrisSet = new HashSet<>(UriUtil.getUrisWithoutScheme(existingUris));
        return !urisSet.equals(existingUrisSet);
    }

}
