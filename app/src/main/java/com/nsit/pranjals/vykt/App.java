package com.nsit.pranjals.vykt;

import android.app.Application;
import android.content.Context;

/**
 * Created by Pranjal on 28-05-2017.
 * App class to provide context.
 */

public class App extends Application {

    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        // Store the application context on application load.
        mContext = this;
    }

    public static Context getContext(){
        return mContext;
    }

}
