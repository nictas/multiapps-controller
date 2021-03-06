package com.sap.cloud.lm.sl.cf.process.steps;

import java.text.MessageFormat;
import java.util.List;

import javax.inject.Named;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.CloudOperationException;
import org.cloudfoundry.client.lib.CloudServiceBrokerException;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudServiceBroker;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

import com.sap.cloud.lm.sl.cf.core.helpers.ApplicationAttributes;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.helpers.ExceptionMessageTailMapper;
import com.sap.cloud.lm.sl.cf.process.helpers.ExceptionMessageTailMapper.CloudComponents;
import com.sap.cloud.lm.sl.cf.process.message.Messages;

@Named("deleteServiceBrokersStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteServiceBrokersStep extends SyncFlowableStep {

    @Override
    protected StepPhase executeStep(ExecutionWrapper execution) {
        getStepLogger().debug(Messages.DELETING_SERVICE_BROKERS);

        List<CloudApplication> appsToUndeploy = StepsUtil.getAppsToUndeploy(execution.getContext());
        CloudControllerClient client = execution.getControllerClient();
        List<String> createdOrUpdatedServiceBrokers = getCreatedOrUpdatedServiceBrokerNames(execution.getContext());

        for (CloudApplication app : appsToUndeploy) {
            deleteServiceBrokerIfNecessary(execution.getContext(), app, createdOrUpdatedServiceBrokers, client);
        }

        getStepLogger().debug(Messages.SERVICE_BROKERS_DELETED);
        return StepPhase.DONE;
    }

    @Override
    protected String getStepErrorMessage(DelegateExecution context) {
        return Messages.ERROR_DELETING_SERVICE_BROKERS;
    }

    @Override
    protected String getStepErrorMessageAdditionalDescription(DelegateExecution context) {
        String offering = StepsUtil.getServiceOffering(context);
        return ExceptionMessageTailMapper.map(configuration, CloudComponents.SERVICE_BROKERS, offering);
    }

    protected List<String> getCreatedOrUpdatedServiceBrokerNames(DelegateExecution context) {
        return StepsUtil.getCreatedOrUpdatedServiceBrokerNames(context);
    }

    private void deleteServiceBrokerIfNecessary(DelegateExecution context, CloudApplication app,
                                                List<String> createdOrUpdatedServiceBrokers, CloudControllerClient client) {
        ApplicationAttributes appAttributes = ApplicationAttributes.fromApplication(app);
        if (!appAttributes.get(SupportedParameters.CREATE_SERVICE_BROKER, Boolean.class, false)) {
            return;
        }
        String name = appAttributes.get(SupportedParameters.SERVICE_BROKER_NAME, String.class, app.getName());

        CloudServiceBroker serviceBroker = client.getServiceBroker(name, false);
        if (serviceBroker != null && !createdOrUpdatedServiceBrokers.contains(name)) {
            try {
                getStepLogger().info(MessageFormat.format(Messages.DELETING_SERVICE_BROKER, name, app.getName()));
                client.deleteServiceBroker(name);
                getStepLogger().debug(MessageFormat.format(Messages.DELETED_SERVICE_BROKER, name, app.getName()));
            } catch (CloudOperationException e) {
                switch (e.getStatusCode()) {
                    case FORBIDDEN:
                        if (shouldSucceed(context)) {
                            getStepLogger().warn(Messages.DELETE_OF_SERVICE_BROKERS_FAILED_403, name);
                            return;
                        }
                        StepsUtil.setServiceOffering(context, Constants.VAR_SERVICE_OFFERING, name);
                        throw new CloudServiceBrokerException(e);
                    case BAD_GATEWAY:
                        StepsUtil.setServiceOffering(context, Constants.VAR_SERVICE_OFFERING, name);
                        throw new CloudServiceBrokerException(e);
                    default:
                        throw e;
                }
            }
        }
    }

    private boolean shouldSucceed(DelegateExecution context) {
        return (Boolean) context.getVariable(Constants.PARAM_NO_FAIL_ON_MISSING_PERMISSIONS);
    }

}
