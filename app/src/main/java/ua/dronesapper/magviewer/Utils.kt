package ua.dronesapper.magviewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

object Utils {
    fun getSharedPref(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.SHARED_KEY_FILE, Context.MODE_PRIVATE)
    }

    fun getRecordsFolder(context: Context): File {
        val root = Environment.getExternalStorageDirectory()
        val appName: String = context.getString(R.string.app_name)
        return File(File(root.absolutePath, Constants.RECORDS_FOLDER), appName)
    }
}