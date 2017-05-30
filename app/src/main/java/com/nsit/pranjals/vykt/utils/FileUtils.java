package com.nsit.pranjals.vykt.utils;

import android.os.Environment;

import java.io.File;

/**
 * Created by Pranjal on 30-05-2017.
 */

public class FileUtils {
    public static String getSVMModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + "svm_data.dat";
        return targetPath;
    }
}
