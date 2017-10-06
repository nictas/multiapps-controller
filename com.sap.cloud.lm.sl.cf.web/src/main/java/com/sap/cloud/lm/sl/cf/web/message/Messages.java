package com.sap.cloud.lm.sl.cf.web.message;

/**
 * A collection of string constants used for exception and logging messages.
 */
public final class Messages {

    public static final String CREATE_DEPLOY_TARGET = "Create deploy target";
    public static final String UPDATE_DEPLOY_TARGET = "Update deploy target";
    public static final String DELETE_DEPLOY_TARGET = "Delete deploy target";

    // Deprecated, used in TargetPlatformsResource_V* for backwards compatibility
    public static final String CREATE_TARGET_PLATFORM = "Create target platform";
    public static final String UPDATE_TARGET_PLATFORM = "Update target platform";
    public static final String DELETE_TARGET_PLATFORM = "Delete target platform";

    // Exception messages
    public static final String ORG_SPACE_NOT_SPECIFIED_1 = "Target does not contain 'org' and 'space' properties";
    public static final String ORG_SPACE_NOT_SPECIFIED_2 = "Target does not contain 'org' and 'space' parameters";
    public static final String ERROR_EXECUTING_REST_API_CALL = "Error occurred while executing REST API call";
    public static final String ONGOING_OPERATION_NOT_FOUND = "Ongoing MTA operation with id \"{0}\" in space \"{1}\" does not exist";
    public static final String CONFIGURATION_ENTRY_ID_CANNOT_BE_UPDATED = "A configuration entry''s id cannot be updated";
    public static final String PROPERTY_DOES_NOT_CONTAIN_KEY_VALUE_PAIR = "Property \"{0}\" does not contain a key value pair";
    public static final String COULD_NOT_PARSE_CONTENT_PARAMETER = "Could not parse content query parameter as JSON or list";
    public static final String COULD_NOT_PARSE_NUMBER = "Could not parse \"{0}\" to number";

    // Audit log messages

    // ERROR log messages
    public static final String MTA_NOT_FOUND = "MTA with id \"{0}\" does not exist";

    public static final String ERROR_INIT_ACTIVITI = "Error initializing Activiti engine";

    public static final String ERROR_STORING_OAUTH_TOKEN_IN_SECURE_STORE = "Error storing OAuth access token with id \"{0}\" in token store. Entry with this id already exists";
    public static final String ERROR_COMPRESSING_OAUTH_TOKEN = "Error compressing OAuth access token";
    public static final String ERROR_DECOMPRESSING_OAUTH_TOKEN = "Error decompressing OAuth access token";
    public static final String TOKEN_NOT_FOUND_IN_SECURE_STORE = "Token not found in secure store";
    public static final String TOKEN_KEY_FORMAT_NOT_VALID = "Token key format not valid";
    public static final String COULD_NOT_PARSE_CONTENT_PARAMETER_AS_JSON = "Could not parse content query parameter as JSON: {0}";
    public static final String COULD_NOT_PARSE_CONTENT_PARAMETER_AS_LIST = "Could not parse content query parameter as list: {0}";
    public static final String ORG_AND_SPACE_MUST_BE_SPECIFIED = "Org and space must be specified!";

    // WARN log messages

    // INFO log messages
    public static final String OAUTH_TOKEN_STORE = "Using OAuth token store \"{0}\"";

    public static final String ALM_SERVICE_ENV_INITIALIZED = "Deploy service environment initialized";

    public static final String AUDIT_LOG_ABOUT_TO_PERFORM_SERVICE_ACTION = "About to perform action \"{0}\" on service \"{1}\"";
    public static final String AUDIT_LOG_SERVICE_ACTION_SUCCESS = "Succesfuly performed action \"{0}\" on service \"{1}\"";
    public static final String AUDIT_LOG_SERVICE_ACTION_FAILURE = "Failed to perform action \"{0}\" on service \"{1}\"";
    public static final String CANNOT_AUTHENTICATE_WITH_CLOUD_CONTROLLER = "Cannot authenticate with cloud controller";

    // DEBUG log messages
    public static final String PARSED__CONTENT = "Parsed content: {0}";
    public static final String COMPUTED_TARGET = "Computed target: {0}";
    public static final String COMPUTING_STATE_OF_OPERATION = "Computing state of operation {0} with ID: {1}";

}