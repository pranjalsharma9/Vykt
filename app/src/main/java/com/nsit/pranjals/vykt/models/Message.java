package com.nsit.pranjals.vykt.models;

import android.graphics.Color;
import android.support.v4.content.ContextCompat;

import com.nsit.pranjals.vykt.App;
import com.nsit.pranjals.vykt.R;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Pranjal on 23-10-2016.
 * The message class.
 */
public class Message implements Externalizable {

    public enum Expression {

        HAPPINESS(R.color.color_happiness, "happy"),
        SADNESS(R.color.color_sadness, "sad"),
        ANGER(R.color.color_anger, "angry"),
        DISGUST(R.color.color_disgust, "disgusted"),
        SURPRISE(R.color.color_surprise, "surprised"),
        FEAR(R.color.color_fear, "scared"),
        NEUTRAL(R.color.color_neutral, "neutral");

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

    private static final long serialVersionUID = 12345L;
    public long timestamp;
    public String sender;
    public String text;
    public Expression expression;

    public Message(long timestamp, String sender, String text, Expression expression) {
        this.timestamp = timestamp;
        this.sender = sender;
        this.text = text;
        this.expression = expression;
    }

    public Message() {
        this.timestamp = 0L;
        this.sender = null;
        this.text = null;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(timestamp);
        out.writeUTF(sender);
        out.writeUTF(text);
        out.writeInt(expression.ordinal());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        timestamp = in.readLong();
        sender = in.readUTF();
        text = in.readUTF();
        expression = Expression.values()[in.readInt()];
    }

}
