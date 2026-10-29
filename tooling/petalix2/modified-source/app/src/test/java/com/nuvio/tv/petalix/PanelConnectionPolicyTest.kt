package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PanelConnectionPolicyTest {
    private val old="https://petalix-control.pinkgiant5.chatgpt.site/api/device/status"
    @Test fun verifiedPanelMigrationKeepsTheSameAuthenticatedApiPath() {
        assertEquals(PanelConnectionPolicy.currentOrigin+"/api/device/status",PanelConnectionPolicy.redirected(old,PanelConnectionPolicy.currentOrigin+"/api/device/status"))
        assertEquals(old,PanelConnectionPolicy.redirected(old,"/api/device/status"))
    }
    @Test fun sessionTokensCannotFollowThirdPartyOrDowngradeRedirects() {
        for(target in listOf("https://other.chatgpt.site/api/device/status","http://petalix-control.caroliinaconsultant.chatgpt.site/api/device/status",PanelConnectionPolicy.currentOrigin+"/api/device/activate",PanelConnectionPolicy.currentOrigin+"/api/device/status?token=anything",PanelConnectionPolicy.currentOrigin+":8443/api/device/status","https://user:pass@petalix-control.caroliinaconsultant.chatgpt.site/api/device/status"))assertNull(PanelConnectionPolicy.redirected(old,target))
    }
}
