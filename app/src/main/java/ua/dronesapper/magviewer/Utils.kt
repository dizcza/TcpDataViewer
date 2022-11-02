package ua.dronesapper.magviewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.view.View
import android.widget.RadioGroup
import androidx.core.view.children
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

    fun radioGroupLoadChecked(dialogView: View, resId: Int, sharedKey: String) {
        val radioGroup: RadioGroup = dialogView.findViewById(resId)
        val idxSaved: Int = getSharedPref(dialogView.context).getInt(sharedKey, 0)
        val radioBtnChecked = radioGroup.children.elementAt(idxSaved)
        radioGroup.check(radioBtnChecked.id)
    }

    fun radioGroupSaveChecked(dialogView: View, resId: Int, sharedKey: String, editor: SharedPreferences.Editor) {
        val radioGroup: RadioGroup = dialogView.findViewById(resId)
        val radioButton: View = radioGroup.findViewById(radioGroup.checkedRadioButtonId)
        val value: Int = radioGroup.indexOfChild(radioButton)
        editor.putInt(sharedKey, value)
    }
}