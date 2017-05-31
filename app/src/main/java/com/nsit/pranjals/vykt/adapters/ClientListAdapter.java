package com.nsit.pranjals.vykt.adapters;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.nsit.pranjals.vykt.R;

import java.util.ArrayList;

/**
 * Created by Pranjal on 24-10-2016.
 * The chat list adapter class.
 */

public class ClientListAdapter extends BaseAdapter {

    private ArrayList<String> clients;
    private OnClientItemSelectedListener listener;

    public ClientListAdapter(ArrayList<String> clients, OnClientItemSelectedListener listener) {
        super();
        this.clients = clients;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return clients.size();
    }

    @Override
    public Object getItem(int i) {
        return clients.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = LayoutInflater.from(viewGroup.getContext()).
                    inflate(R.layout.client_item, viewGroup, false);
        }

        final String clientInfo = (String) getItem(i);
        String[] clientInfoArray = clientInfo.split(":");

        TextView clientName = (TextView) view.findViewById(R.id.tv_client_name_client_item);
        TextView clientId = (TextView) view.findViewById(R.id.tv_client_id_client_item);

        clientName.setText(clientInfoArray[1]);
        clientId.setText(clientInfoArray[0]);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onClientItemSelected(clientInfo);
            }
        });

        return view;
    }

    // Interface to handle clicks.
    public interface OnClientItemSelectedListener {
        void onClientItemSelected (String client);
    }

}
