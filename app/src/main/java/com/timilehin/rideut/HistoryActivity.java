package com.timilehin.rideut;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.timilehin.rideut.HistoryRecyclerView.History;
import com.timilehin.rideut.HistoryRecyclerView.HistoryAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity
    {
        private String userMode, userId;

        private RecyclerView mHistoryRecyclerView;
        private RecyclerView.Adapter mHistoryAdapter;
        private RecyclerView.LayoutManager mHistoryLayoutManager;

        private final int CONVERT_TIME_FROM_SECONDS = 1000;

        private ArrayList rideHistoryList = new ArrayList<History>();

        @Override
        protected void onCreate(Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_history);

                /* Set up the recycler view.
                 * Enable natural scrolling. */
                mHistoryRecyclerView = findViewById(R.id.historyRecyclerView);
                mHistoryRecyclerView.setNestedScrollingEnabled(false);
                mHistoryRecyclerView.setHasFixedSize(true);

                Log.e("HistoryActivityCreate", "recyclerView set up properly...");

                mHistoryLayoutManager = new LinearLayoutManager(HistoryActivity.this);
                mHistoryRecyclerView.setLayoutManager(mHistoryLayoutManager);
                mHistoryAdapter = new HistoryAdapter(getRideHistory(), HistoryActivity.this);
                mHistoryRecyclerView.setAdapter(mHistoryAdapter);

                Log.e("HistoryActivityCreate", "The adapter and layout manager set up properly...");

                userMode = getIntent().getExtras().getString("UserMode");
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                Log.e("HistoryActivityCreate", "got userID and userMode set up properly...");

                getUserHistoryIds();

                Log.e("HistoryActivityCreate", "Got all the IDs...");
            }

        private ArrayList<History> getRideHistory()
            {
                return rideHistoryList;
            }

        public void getUserHistoryIds()
            {
                DatabaseReference userHistoryDatabase = FirebaseDatabase.getInstance().getReference()
                                                                                      .child("Users")
                                                                                      .child(userMode)
                                                                                      .child(userId)
                                                                                      .child("history");
                userHistoryDatabase.addListenerForSingleValueEvent(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                Log.e("getHistory", "data has changed...");
                                if (dataSnapshot.exists())
                                    {
                                        Log.e("getHistory", "dataSnapshot exists...");

                                        for (DataSnapshot history : dataSnapshot.getChildren())
                                            {
                                                Log.i("inGetUserHistory", "fetching...");
                                                FetchRideInfo(history.getKey());
                                            }
                                    }
                            }

                        @Override
                        public void onCancelled(DatabaseError databaseError)
                            {

                            }
                    });
            }

        /* We can use a for loop because it is a single event listener */
        private void FetchRideInfo(String rideKey)
            {
                DatabaseReference currentRideHistoryDetails = FirebaseDatabase.getInstance().getReference()
                                                                                            .child("History")
                                                                                            .child(rideKey);

                currentRideHistoryDetails.addListenerForSingleValueEvent(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                Log.i("inFetchOnDataChange", "there was data change...");
                                Long rideTimeStamp = 0L;
                                if (dataSnapshot.exists())
                                    {
                                        String rideId = dataSnapshot.getKey();

                                        for (DataSnapshot child: dataSnapshot.getChildren())
                                            {
                                                if (child.getKey().equals("timeStamp"))
                                                    {
                                                        rideTimeStamp = Long.valueOf(child.getValue().toString());
                                                    }
                                            }
                                        History newHistoryEntry = new History(rideId, getRideDate(rideTimeStamp));
                                        rideHistoryList.add(newHistoryEntry);
                                        mHistoryAdapter.notifyDataSetChanged();
                                    }
                            }

                        @Override
                        public void onCancelled(DatabaseError databaseError)
                            {

                            }
                    });
                Log.i("inFetchOnDataChange", "should pair if there was a change...");

            }

        /* Helper method to convert the date stored on the database into human readable date format. */
        private String getRideDate(Long rideTimeStamp)
            {
                Calendar cal = Calendar.getInstance(Locale.getDefault());
                cal.setTimeInMillis(rideTimeStamp * CONVERT_TIME_FROM_SECONDS);
                String date = DateFormat.format("dd-MM-yyyy hh:mm", cal).toString();
                return date;
            }
    }
