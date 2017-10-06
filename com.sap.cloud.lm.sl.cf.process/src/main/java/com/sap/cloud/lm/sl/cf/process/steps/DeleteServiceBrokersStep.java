package com.sap.cloud.lm.sl.cf.process.steps;

import static com.sap.cloud.lm.sl.cf.process.steps.CreateOrUpdateServiceBrokersStep.getServiceBrokerNames;

import java.text.MessageFormat;
import java.util.List;

import org.activiti.engine.delegate.DelegateExecution;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.sap.activiti.common.ExecutionStatus;
import com.sap.cloud.lm.sl.cf.core.helpers.ApplicationAttributesGetter;
import com.sap.cloud.lm.sl.cf.core.model.SupportedParameters;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.slp.model.StepMetadata;

@Component("deleteServiceBrokersStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteServiceBrokersStep extends AbstractXS2ProcessStep {

    public static StepMetadata getMetadata() {
        return StepMetadata.builder().id("deleteServiceBrokersTask").displayName("Delete Service Brokers").description(
            "Delete Service Brokers").build();
    }

    @Override
    protected ExecutionStatus executeStepInternal(DelegateExecution context) throws SLException {
        getStepLogger().logActivitiTask();
        try {
            getStepLogger().info(Messages.DELETING_SERVICE_BROKERS);

            List<CloudApplication> appsToUndeploy = StepsUtil.getAppsToUndeploy(context);
            CloudFoundryOperations client = getCloudFoundryClient(context);
            List<String> serviceBrokersToCreate = getServiceBrokerNames(StepsUtil.getServiceBrokersToCreate(context));

            for (CloudApplication app : appsToUndeploy) {
                deleteServiceBrokerIfNecessary(context, app, serviceBrokersToCreate, client);
            }

            getStepLogger().debug(Messages.SERVICE_BROKERS_DELETED);
            return ExecutionStatus.SUCCESS;
        } catch (SLException e) {
            getStepLogger().error(e, Messages.ERROR_DELETING_SERVICE_BROKERS);
            throw e;
        } catch (CloudFoundryException cfe) {
            SLException e = StepsUtil.createException(cfe);
            getStepLogger().error(e, Messages.ERROR_DELETING_SERVICE_BROKERS);
            throw e;
        }
    }

    private void deleteServiceBrokerIfNecessary(DelegateExecution context, CloudApplication app, List<String> serviceBrokersToCreate,
        CloudFoundryOperations client) throws SLException {
        ApplicationAttributesGetter attributesGetter = ApplicationAttributesGetter.forApplication(app);
        if (!attributesGetter.getAttribute(SupportedParameters.CREATE_SERVICE_BROKER, Boolean.class, false)) {
            return;
        }
        String name = attributesGetter.getAttribute(SupportedParameters.SERVICE_BROKER_NAME, String.class, app.getName());

        if (serviceBrokerExists(name, client) && !serviceBrokersToCreate.contains(name)) {
            try {
                getStepLogger().info(MessageFormat.format(Messages.DELETING_SERVICE_BROKER, name, app.getName()));
                client.deleteServiceBroker(name);
                getStepLogger().debug(MessageFormat.format(Messages.DELETED_SERVICE_BROKER, name, app.getName()));
            } catch (CloudFoundryException e) {
                switch (e.getStatusCode()) {
                    case FORBIDDEN:
                        if (shouldSucceed(context)) {
                            getStepLogger().warn(Messages.DELETE_OF_SERVICE_BROKERS_FAILED_403, name);
                            return;
                        }
                    default:
                        throw e;
                }
            }
        }
    }

    private boolean serviceBrokerExists(String serviceBrokerName, CloudFoundryOperations client) {
        try {
            client.getServiceBroker(serviceBrokerName);
        } catch (CloudFoundryException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return false;
            }
            throw e;
        }
        return true;
    }

    private boolean shouldSucceed(DelegateExecution context) {
        return (Boolean) context.getVariable(Constants.PARAM_NO_FAIL_ON_MISSING_PERMISSIONS);
    }

}
