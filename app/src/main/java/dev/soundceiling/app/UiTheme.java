package dev.soundceiling.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class UiTheme {
    static boolean isDark(Context context) {
        String mode = Prefs.themeMode(context);
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static int background(Context context) { return isDark(context) ? Color.rgb(16,17,20) : Color.rgb(246,247,249); }
    static int surface(Context context) { return isDark(context) ? Color.rgb(29,31,36) : Color.WHITE; }
    static int primaryText(Context context) { return isDark(context) ? Color.WHITE : Color.rgb(25,27,31); }
    static int secondaryText(Context context) { return isDark(context) ? Color.rgb(185,190,203) : Color.rgb(82,87,98); }
    static int meterTrack(Context context) { return isDark(context) ? Color.rgb(48,52,60) : Color.rgb(226,229,235); }
    static int meterFill(Context context) { return isDark(context) ? Color.rgb(112,178,146) : Color.rgb(91,143,119); }
    static int meterGrid(Context context) { return isDark(context) ? Color.rgb(83,88,99) : Color.rgb(198,203,212); }

    static int successSurface(Context context) {
        return isDark(context) ? Color.rgb(24,64,42) : Color.rgb(225,246,233);
    }

    static int successText(Context context) {
        return isDark(context) ? Color.rgb(190,245,210) : Color.rgb(24,94,52);
    }

    static int warningSurface(Context context) {
        return isDark(context) ? Color.rgb(82,66,25) : Color.rgb(255,245,211);
    }

    static int warningText(Context context) {
        return isDark(context) ? Color.rgb(255,226,145) : Color.rgb(111,78,0);
    }

    static int errorSurface(Context context) {
        return isDark(context) ? Color.rgb(84,34,37) : Color.rgb(253,232,232);
    }

    static int errorText(Context context) {
        return isDark(context) ? Color.rgb(255,198,200) : Color.rgb(150,35,40);
    }

    static int neutralStatusSurface(Context context) {
        return isDark(context) ? Color.rgb(48,51,58) : Color.rgb(232,235,240);
    }

    static int neutralStatusText(Context context) {
        return isDark(context) ? Color.rgb(235,238,245) : Color.rgb(42,46,54);
    }

    static void applyActivityTheme(Activity activity) {
        boolean dark = isDark(activity);
        activity.setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        activity.getWindow().setStatusBarColor(background(activity));
        activity.getWindow().setNavigationBarColor(background(activity));
        activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    static void applyToTree(View view) {
        Context context = view.getContext();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyToTree(group.getChildAt(i));
        }
        if (view instanceof TextView) ((TextView) view).setTextColor(primaryText(context));
    }

    private UiTheme() {}
}
