package com.nsit.pranjals.vykt;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;

/**
 * Created by Pranjal on 28-05-2017.
 * App class to provide context.
 */

public class App extends Application {

    private static Context mContext;
    public static String userId;

    @Override
    public void onCreate() {
        super.onCreate();
        // Store the application context on application load.
        mContext = this;
        userId = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    public static Context getContext(){
        return mContext;
    }

}
