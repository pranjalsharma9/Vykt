package com.nsit.pranjals.vykt.adapters;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.nsit.pranjals.vykt.App;
import com.nsit.pranjals.vykt.R;
import com.nsit.pranjals.vykt.models.Message;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by Pranjal on 24-10-2016.
 * The chat list adapter class.
 */

public class ChatListAdapter extends BaseAdapter {

    private ArrayList<Message> messages;

    public ChatListAdapter(ArrayList<Message> messages) {
        super();
        this.messages = messages;
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Object getItem(int i) {
        return messages.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = LayoutInflater.from(viewGroup.getContext()).
                    inflate(R.layout.chat_bubble, viewGroup, false);
        }

        Message message = (Message) getItem(i);

        TextView messageTime = (TextView) view.findViewById(R.id.tv_chat_bubble_time);
        TextView messageSender = (TextView) view.findViewById(R.id.tv_chat_bubble_sender);
        TextView messageContent = (TextView) view.findViewById(R.id.tv_chat_bubble_content);
        TextView messageExpression = (TextView) view.findViewById(R.id.tv_chat_bubble_expression);
        if (message.timestamp != 0L) {
            messageTime.setText(
                    String.format(Locale.getDefault(), "%02d:%02d:%02d",
                            TimeUnit.MILLISECONDS.toHours(message.timestamp) %
                                    TimeUnit.DAYS.toHours(1),
                            TimeUnit.MILLISECONDS.toMinutes(message.timestamp) %
                                    TimeUnit.HOURS.toMinutes(1),
                            TimeUnit.MILLISECONDS.toSeconds(message.timestamp) %
                                    TimeUnit.MINUTES.toSeconds(1))
            );
        } else {
            messageTime.setText("");
        }
        String senderName = message.sender.split(":")[1];
        messageSender.setText(senderName);
        messageContent.setText(message.text);
        messageExpression.setTextColor(message.expression.getColor());
        messageExpression.setText(message.expression.getStateString());
        View wrapperView = view.findViewById(R.id.wrapper_chat_bubble);
        Drawable background = wrapperView.getBackground();
        background.setTint(message.expression.getBgColor());
        wrapperView.setBackground(background);

        if (message.sender.equals(App.userId)) {
            view.setPadding(100, 0, 0, 0);
        } else {
            view.setPadding(0, 0, 100, 0);
        }

        // returns the view for the current row
        return view;
    }
}
