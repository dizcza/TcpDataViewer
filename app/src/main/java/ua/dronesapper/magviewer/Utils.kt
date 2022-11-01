package ua.dronesapper.magviewer

import android.content.Context
import android.content.SharedPreferences

object Utils {
    fun getSharedPref(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.SHARED_KEY_FILE, Context.MODE_PRIVATE)
    }
}