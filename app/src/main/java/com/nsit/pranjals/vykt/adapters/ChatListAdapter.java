package com.nsit.pranjals.vykt.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

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

    public ArrayList<Message> messages;
    public ListView parentListView;

    public ChatListAdapter(ArrayList<Message> messages, ListView parentListView) {
        super();
        this.messages = messages;
        this.parentListView = parentListView;
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
        messageSender.setText(message.sender);
        messageContent.setText(message.text);

        // returns the view for the current row
        return view;
    }

    public ListView getParentListView () {
        return parentListView;
    }
}
