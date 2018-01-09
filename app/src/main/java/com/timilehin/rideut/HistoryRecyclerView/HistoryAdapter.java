package com.timilehin.rideut.HistoryRecyclerView;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.timilehin.rideut.R;

import java.util.List;

/**
 * Created by tijanioluwatimilehin on 1/7/18.
 */

public class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolders>
    {
        private List<History> rideHistoryList;
        private Context context;

        public HistoryAdapter(List<History> rideList, Context context)
            {
                this.rideHistoryList = rideList;
                this.context = context;
            }

        /* Inflate the recycler view that we created for each ride ID.
         * Then force the recycler view to fill the available space using MATCH_PARENT and WRAP_CONTENT
         * Finally, create a new instance of the HistoryViewHolders that uses the layout we created and return it. */
        @Override
        public HistoryViewHolders onCreateViewHolder(ViewGroup parent, int viewType)
            {
                View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.ride_history, null, false);
                RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutView.setLayoutParams(lp);
                HistoryViewHolders rcv = new HistoryViewHolders(layoutView);
                return rcv;
            }

        @Override
        public void onBindViewHolder(HistoryViewHolders holder, int position)
            {
                holder.rideId.setText(rideHistoryList.get(position).getRideId());
                holder.time.setText(rideHistoryList.get(position).getTime());

            }

        @Override
        public int getItemCount()
            {
                return this.rideHistoryList.size();
            }
    }
