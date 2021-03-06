package com.sap.cloud.lm.sl.cf.client.uaa;

import java.net.URL;

import org.cloudfoundry.client.lib.util.RestUtil;

public class UAAClientFactory {

    public UAAClient createClient(URL uaaUrl) {
        return new UAAClient(uaaUrl, new RestUtil().createRestTemplate(null, false));
    }

}
