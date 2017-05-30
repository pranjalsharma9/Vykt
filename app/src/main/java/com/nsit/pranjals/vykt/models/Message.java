package com.nsit.pranjals.vykt.models;

import com.nsit.pranjals.vykt.enums.Expression;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Pranjal on 23-10-2016.
 * The message class.
 */
public class Message implements Externalizable {

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
