package com.timilehin.rideut.HistoryRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.timilehin.rideut.HistorySingleActivity;
import com.timilehin.rideut.R;

/**
 * Created by tijanioluwatimilehin on 1/7/18.
 */

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        public TextView rideId, time;

        public HistoryViewHolders(View itemView)
            {
                super(itemView);
                itemView.setOnClickListener(this);

                rideId = (TextView) itemView.findViewById(R.id.rideId);
                time = (TextView) itemView.findViewById(R.id.rideTime);
            }

        /* The listener for each ride in the list.
         * It creates and starts an intent that goes to another activity that will display more
         * detail on the selected ride. */
        @Override
        public void onClick(View view)
            {
                Intent intent = new Intent(view.getContext(), HistorySingleActivity.class);
                Bundle info = new Bundle();
                info.putString("rideId", rideId.getText().toString());
                intent.putExtras(info);
                view.getContext().startActivity(intent);

            }
    }
