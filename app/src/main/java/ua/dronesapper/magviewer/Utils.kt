package ua.dronesapper.magviewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.view.View
import android.widget.RadioGroup
import androidx.core.view.children
import java.io.File

object Utils {
    private val resourceMapper = mapOf(
        SharedKey.SERVER_PORT to R.id.server_port,
        SharedKey.DATA_TYPE to R.id.data_type,
        SharedKey.ENDIAN to R.id.data_endian,
        SharedKey.PLOT_KEEP_LAST_COUNT to R.integer.plot_size_default,
        SharedKey.PLOT_UPDATE_PERIOD to R.integer.update_period_default
    )

    fun getSharedPref(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.SHARED_KEY_FILE, Context.MODE_PRIVATE)
    }

    fun getRecordsFolder(context: Context): File {
        val root = Environment.getExternalStorageDirectory()
        val appName: String = context.getString(R.string.app_name)
        return File(File(root.absolutePath, Constants.RECORDS_FOLDER), appName)
    }

    fun getInteger(context: Context, sharedKey: String): Int {
        return getSharedPref(context).getInt(sharedKey, context.resources.getInteger(resourceMapper[sharedKey]!!))
    }

    fun radioGroupLoadChecked(dialogView: View, sharedKey: String) {
        val radioGroup: RadioGroup = dialogView.findViewById(resourceMapper[sharedKey]!!)
        val idxSaved: Int = getSharedPref(dialogView.context).getInt(sharedKey, 0)
        val radioBtnChecked = radioGroup.children.elementAt(idxSaved)
        radioGroup.check(radioBtnChecked.id)
    }

    fun radioGroupSaveChecked(dialogView: View, sharedKey: String, editor: SharedPreferences.Editor) {
        val radioGroup: RadioGroup = dialogView.findViewById(resourceMapper[sharedKey]!!)
        val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
        val value: Int = radioGroup.indexOfChild(radioButton)
        editor.putInt(sharedKey, value)
    }
}