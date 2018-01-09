package com.timilehin.rideut;

import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistorySingleActivity extends AppCompatActivity implements OnMapReadyCallback, RoutingListener
    {
        private String currentUserId, customerId, driverId, userMode;

        private String rideID;
        private GoogleMap mMap;
        private SupportMapFragment mMapFragment;

        private TextView mDate;
        private TextView mUserName;
        private TextView mLocation;
        private TextView mDistance;
        private TextView mUserPhoneNumber;

        private DatabaseReference rideHistoryInfoDatabase;
        private final int CONVERT_TIME_FROM_SECONDS = 1000;

        private ImageView mUserImage;
        private LatLng pickupLatLng, destinationLatLng;

        private List<Polyline> polylines;
        private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

        @Override
        protected void onCreate(Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_history_single);

                rideID = getIntent().getExtras().getString("rideId");

                // Must be done!!
                polylines = new ArrayList<>();

                /* Set up the map view in the rideMap fragment to show the ride path. */
                mMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.rideMap);
                mMapFragment.getMapAsync(this);

                mDate = findViewById(R.id.rideDate);
                mUserName = findViewById(R.id.userName);
                mLocation = findViewById(R.id.rideLocation);
                mDistance = findViewById(R.id.rideDistance);
                mUserPhoneNumber = findViewById(R.id.userPhone);

                mUserImage = findViewById(R.id.userImage);

                currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                rideHistoryInfoDatabase = FirebaseDatabase.getInstance().getReference().child("History").child(rideID);
                getRideInfo();
            }

        private void getRideInfo()
            {
                rideHistoryInfoDatabase.addListenerForSingleValueEvent(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                if (dataSnapshot.exists())
                                    {
                                        for (DataSnapshot child : dataSnapshot.getChildren())
                                            {
                                                if (child.getKey().equals("customer"))
                                                    {
                                                        customerId = child.getValue().toString();
                                                        if (!currentUserId.equals(customerId))
                                                            {
                                                                userMode = "Drivers";
                                                                getUserInformation("Customers", customerId);
                                                            }

                                                    }

                                                if (child.getKey().equals("driver"))
                                                    {
                                                        driverId = child.getValue().toString();
                                                        if (!currentUserId.equals(driverId))
                                                            {
                                                                userMode = "Customers";
                                                                getUserInformation("Drivers", driverId);
                                                            }
                                                    }

                                                if (child.getKey().equals("timestamp"))
                                                    {
                                                        mDate.setText(getRideDate(Long.valueOf(child.getValue().toString())));

                                                    }

                                                if (child.getKey().equals("destination"))
                                                    {
                                                        mLocation.setText(getRideDate(Long.valueOf(child.getValue().toString())));
                                                    }

                                                if (child.getKey().equals("location"))
                                                    {
                                                        pickupLatLng = new LatLng(Double.valueOf(child.child("from").child("lat").getValue().toString()),
                                                                                  Double.valueOf(child.child("from").child("lng").getValue().toString()));

                                                        destinationLatLng = new LatLng(Double.valueOf(child.child("to").child("lat").getValue().toString()),
                                                                Double.valueOf(child.child("to").child("lng").getValue().toString()));

                                                        if (destinationLatLng != new LatLng(0.0, 0.0))
                                                            getRouteToMarker();
                                                    }
                                            }
                                    }
                            }

                        @Override
                        public void onCancelled(DatabaseError databaseError)
                            {

                            }
                    });
            }

        /* otherUser refers to the driver if the current user is the customer and vice versa. */
        private void getUserInformation(String otherUser, String otherUserId)
            {
                DatabaseReference mOtherUserDB = FirebaseDatabase.getInstance().getReference()
                                                                              .child("Users")
                                                                              .child(otherUser)
                                                                              .child(otherUserId);
                mOtherUserDB.addListenerForSingleValueEvent(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                if (dataSnapshot.exists())
                                    {
                                        Map<String, Object> otherUserInfo = (Map<String, Object>) dataSnapshot.getValue();

                                        if (otherUserInfo.get("name") != null)
                                            mUserName.setText(otherUserInfo.get("name").toString());

                                        if (otherUserInfo.get("phone") != null)
                                            mUserPhoneNumber.setText(otherUserInfo.get("phone").toString());

                                        if (otherUserInfo.get("profileImageUri") != null)
                                            {
                                                Glide.with(getApplication()).load(otherUserInfo
                                                                            .get("profileImageUri")
                                                                            .toString())
                                                                            .into(mUserImage);
                                            }
                                    }
                            }

                        @Override
                        public void onCancelled(DatabaseError databaseError)
                            {

                            }
                    });
            }

        /* From OnMapReadyCallback
         * This method is necessary for the map to be displayed in the fragment. */
        @Override
        public void onMapReady(GoogleMap googleMap)
            {
                mMap = googleMap;
            }


        /* Clear all each individual line from the map.
         * Clear the polyline arrayList as well. */
        private void erasePolyLines()
            {
                for (Polyline line : polylines)
                    line.remove();

                polylines.clear();
            }

        @Override
        public void onRoutingFailure(RouteException e)
            {
                if(e != null)
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
            }

        @Override
        public void onRoutingStart()
            {

            }

        @Override
        public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex)
            {
                /* Code section for adding markers and zooming into locations. */
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                builder.include(pickupLatLng);
                builder.include(destinationLatLng);
                LatLngBounds bounds = builder.build();

                int width = getResources().getDisplayMetrics().widthPixels;
                int padding = (int) (width * 0.2);

                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);
                mMap.animateCamera(cameraUpdate);

                mMap.addMarker(new MarkerOptions().position(pickupLatLng)
                                                  .title("Pickup Location")
                                                  .icon(BitmapDescriptorFactory
                                                  .fromResource(R.mipmap.ic_driver_car)));
                mMap.addMarker(new MarkerOptions().position(destinationLatLng)
                                                  .title("Destination")
                                                  .icon(BitmapDescriptorFactory
                                                  .fromResource(R.mipmap.ic_pickup_location)));



                if(polylines.size()>0)
                    {
                        for (Polyline poly : polylines)
                            {
                                poly.remove();
                            }
                    }

                polylines = new ArrayList<>();
                //add route(s) to the map.
                for (int i = 0; i <route.size(); i++)
                    {

                        //In case of more than 5 alternative routes
                        int colorIndex = i % COLORS.length;

                        PolylineOptions polyOptions = new PolylineOptions();
                        polyOptions.color(getResources().getColor(COLORS[colorIndex]));
                        polyOptions.width(10 + i * 3);
                        polyOptions.addAll(route.get(i).getPoints());
                        Polyline polyline = mMap.addPolyline(polyOptions);
                        polylines.add(polyline);

                        Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
                    }
            }

        @Override
        public void onRoutingCancelled()
            {

            }

        private void getRouteToMarker()
            {
                Routing routing = new Routing.Builder()
                        .travelMode(AbstractRouting.TravelMode.DRIVING)
                        .withListener(this)
                        .alternativeRoutes(false)
                        .waypoints(pickupLatLng, destinationLatLng)
                        .build();
                routing.execute();
            }

        private String getRideDate(Long rideTimeStamp)
            {
                Calendar cal = Calendar.getInstance(Locale.getDefault());
                cal.setTimeInMillis(rideTimeStamp * CONVERT_TIME_FROM_SECONDS);
                String date = DateFormat.format("dd-MM-yyyy hh:mm", cal).toString();
                return date;
            }
    }
