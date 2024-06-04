package com.chaquo.python.utils

import android.content.Context
import android.content.res.Resources

public class Utils {
    /**
     * This method retrieves a resource ID from the application's resources.
     * It avoids direct "R" references to make this package easy to copy to other apps.
     */
    public static int resId(Context context, String type, String name) {
        // Get the Resources object from the Context
        Resources resources = context.getResources()

        // Get the resource ID
        return resources.getIdentifier(name, type, context.getApplicationInfo().packageName)
    }
}