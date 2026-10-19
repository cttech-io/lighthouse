package io.cttech.lighthouse.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Whether Lighthouse is Android's default Home app, and how to ask for it. */
object HomeRole {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.let { it.isRoleAvailable(RoleManager.ROLE_HOME) && it.isRoleHeld(RoleManager.ROLE_HOME) } == true

    /** The system's "Set Lighthouse as your default home app?" dialog; null if the role can't be requested. */
    fun requestIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }?.createRequestRoleIntent(RoleManager.ROLE_HOME)

    /** Fallback: Android's Default apps → Home app screen. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
