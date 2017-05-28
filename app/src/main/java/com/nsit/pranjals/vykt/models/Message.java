package com.nsit.pranjals.vykt.models;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by Pranjal on 23-10-2016.
 */
public class Message implements Externalizable {

    private static final long serialVersionUID = 12345L;
    public long timestamp;
    public String sender;
    public String text;

    public Message(long timestamp, String sender, String text) {
        this.timestamp = timestamp;
        this.sender = sender;
        this.text = text;
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
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        timestamp = in.readLong();
        sender = in.readUTF();
        text = in.readUTF();
    }

}
