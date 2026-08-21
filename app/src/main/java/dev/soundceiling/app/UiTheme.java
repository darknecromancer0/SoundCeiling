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

    static void applyActivityTheme(Activity activity) {
        boolean dark = isDark(activity);
        activity.setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        activity.getWindow().setStatusBarColor(background(activity));
        activity.getWindow().setNavigationBarColor(background(activity));
        activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    static void applyToTree(View view) {
        Context context = view.getContext();
        view.setBackgroundColor(view instanceof ViewGroup ? background(context) : view.getSolidColor());
        if (view instanceof TextView) ((TextView) view).setTextColor(primaryText(context));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyToTree(group.getChildAt(i));
        }
    }

    private UiTheme() {}
}
