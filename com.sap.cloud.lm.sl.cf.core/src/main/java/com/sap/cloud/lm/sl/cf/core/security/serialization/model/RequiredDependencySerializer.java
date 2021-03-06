package com.sap.cloud.lm.sl.cf.core.security.serialization.model;

import com.sap.cloud.lm.sl.cf.core.security.serialization.SecureJsonSerializer;
import com.sap.cloud.lm.sl.cf.core.security.serialization.SecureSerializerConfiguration;
import com.sap.cloud.lm.sl.cf.core.security.serialization.masking.RequiredDependencyMasker;
import com.sap.cloud.lm.sl.mta.model.RequiredDependency;

public class RequiredDependencySerializer extends SecureJsonSerializer {

    private final RequiredDependencyMasker masker = new RequiredDependencyMasker();

    public RequiredDependencySerializer(SecureSerializerConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String serialize(Object object) {
        RequiredDependency clonedDependency = RequiredDependency.copyOf((RequiredDependency) object);
        masker.mask(clonedDependency);
        return super.serialize(clonedDependency);
    }

}
