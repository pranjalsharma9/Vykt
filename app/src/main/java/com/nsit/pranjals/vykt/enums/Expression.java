package com.nsit.pranjals.vykt.enums;

import android.graphics.Color;
import android.support.v4.content.ContextCompat;

import com.nsit.pranjals.vykt.App;
import com.nsit.pranjals.vykt.R;

/**
 * Created by Pranjal on 31-05-2017.
 * Expression enumeration.
 */
public enum Expression {

    NEUTRAL(R.color.color_neutral, "neutral"),
    ANGER(R.color.color_anger, "angry"),
    DISGUST(R.color.color_disgust, "disgusted"),
    HAPPINESS(R.color.color_happiness, "happy"),
    SURPRISE(R.color.color_surprise, "surprised"),
    SADNESS(R.color.color_sadness, "sad"),
    FEAR(R.color.color_fear, "scared");

    private int color;
    private String stateString;

    Expression (int colorResId, String stateString) {
        this.color = ContextCompat.getColor(App.getContext(), colorResId);
        this.stateString = stateString;
    }

    public int getColor() {
        return color;
    }

    public int getBgColor () {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = 0.18f;
        hsv[2] = 1.0f;
        return Color.HSVToColor(hsv);
    }

    public String getStateString () {
        return stateString;
    }

}
