package com.sap.cloud.lm.sl.cf.process.steps;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.cf.process.util.ProcessTypeParser;
import com.sap.cloud.lm.sl.cf.web.api.model.ProcessType;
import com.sap.cloud.lm.sl.mta.model.Module;

@Named("prepareModulesDeploymentStep")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PrepareModulesDeploymentStep extends SyncFlowableStep {

    @Inject
    protected ProcessTypeParser processTypeParser;

    @Override
    protected StepPhase executeStep(ExecutionWrapper execution) {
        getStepLogger().debug(Messages.PREPARING_MODULES_DEPLOYMENT);

        // Get the list of cloud modules from the context:
        List<Module> modulesToDeploy = getModulesToDeploy(execution.getContext());

        // Initialize the iteration over the applications list:
        execution.getContext()
                 .setVariable(Constants.VAR_MODULES_COUNT, modulesToDeploy.size());
        execution.getContext()
                 .setVariable(Constants.VAR_MODULES_INDEX, 0);
        execution.getContext()
                 .setVariable(Constants.VAR_INDEX_VARIABLE_NAME, Constants.VAR_MODULES_INDEX);

        execution.getContext()
                 .setVariable(Constants.REBUILD_APP_ENV, true);
        execution.getContext()
                 .setVariable(Constants.SHOULD_UPLOAD_APPLICATION_CONTENT, true);
        execution.getContext()
                 .setVariable(Constants.EXECUTE_ONE_OFF_TASKS, true);

        StepsUtil.setModulesToDeploy(execution.getContext(), modulesToDeploy);

        ProcessType processType = processTypeParser.getProcessType(execution.getContext());

        StepsUtil.setDeleteIdleUris(execution.getContext(), false);
        StepsUtil.setSkipUpdateConfigurationEntries(execution.getContext(), ProcessType.BLUE_GREEN_DEPLOY.equals(processType));
        StepsUtil.setSkipManageServiceBroker(execution.getContext(), ProcessType.BLUE_GREEN_DEPLOY.equals(processType));
        StepsUtil.setUseIdleUris(execution.getContext(), ProcessType.BLUE_GREEN_DEPLOY.equals(processType));

        return StepPhase.DONE;
    }

    @Override
    protected String getStepErrorMessage(DelegateExecution context) {
        return Messages.ERROR_PREPARING_MODULES_DEPLOYMENT;
    }

    protected List<Module> getModulesToDeploy(DelegateExecution context) {
        return StepsUtil.getAllModulesToDeploy(context);
    }

}
