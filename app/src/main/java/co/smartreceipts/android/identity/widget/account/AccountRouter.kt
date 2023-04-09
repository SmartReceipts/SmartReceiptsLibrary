package co.smartreceipts.android.identity.widget.account

import co.smartreceipts.android.activities.NavigationHandler
import co.smartreceipts.android.activities.SmartReceiptsActivity
import co.smartreceipts.core.di.scopes.FragmentScope
import co.smartreceipts.core.identity.IdentityManager
import co.smartreceipts.analytics.log.Logger
import javax.inject.Inject

@FragmentScope
class AccountRouter @Inject constructor(
    private val navigationHandler: NavigationHandler<SmartReceiptsActivity>,
    private val identityManager: IdentityManager
) {

    /**
     * Navigates us to the proper next screen: nowhere if we're logged in, back if the user was previously
     * navigated away and returned here (eg via the backstack), or to the login screen if not logged in
     *
     * @param wasPreviouslyNavigated `true` if the user was previously navigated away
     * @return `true` if we are sent to the login screen. `false` otherwise
     */
    fun navigateToProperLocation(wasPreviouslyNavigated: Boolean): Boolean {
        if (!identityManager.isLoggedIn) {
            if (!wasPreviouslyNavigated) {
                Logger.info(this, "User not logged in. Sending to the log in screen")
                navigationHandler.navigateToLoginScreen()
                return true
            } else {
                Logger.info(
                    this,
                    "Returning to this fragment after not signing in. Navigating back rather than looping back to the log in screen"
                )
                this.navigationHandler.navigateBackDelayed()
            }
        } else {
            Logger.debug(this, "User is already logged in. Doing nothing and remaining on this screen")
        }
        return false
    }

    fun navigateBack(): Boolean {
        return this.navigationHandler.navigateBack()
    }

    fun navigateToOcrFragment() {
        if(identityManager.isLoggedIn)
        navigationHandler.navigateToOcrConfigurationFragment()
        else navigationHandler.navigateToLoginScreen()
    }
}