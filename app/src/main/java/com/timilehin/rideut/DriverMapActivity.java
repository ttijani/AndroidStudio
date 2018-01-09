package com.timilehin.rideut;

import android.*;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ObjectsCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.sql.Driver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.timilehin.rideut.R.id.map;
import static com.timilehin.rideut.R.id.time;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, RoutingListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mlastLocation;
    private LocationRequest mlocationRequest;

    public static final int REQUEST_LOCATION_CODE = 99;

    private SupportMapFragment mapFragment;

    private Button mLogOut, mSettings, mRideStatus;

    private LinearLayout mCustomerInfo;
    private ImageView mCustomerProfileImage;
    private TextView mCustomerName, mCustomerPhone, mCustomerDestination;

    private boolean isLoggingOut = false;
    private String customerId = "";
    private Marker mCustomerMarker;
    private LatLng pickupLatLng;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    private final int CONVERT_TIME_TO_SECONDS = 1000;

    private final int DEFAULT_DRIVER_STATUS = 0;
    private final int GOING_TO_PICKUP = 1;
    private final int RIDE_STARTED = 2;

    private int status = DEFAULT_DRIVER_STATUS;
    private String destination;
    private LatLng destinationLatLng;


    @Override
    protected void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_driver_map_activity);

            polylines = new ArrayList<>();

            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(map);

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(DriverMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
                }
            else
                {
                    mapFragment.getMapAsync(this);
                }

            mLogOut = findViewById(R.id.logout);
            mSettings = findViewById(R.id.settings);
            mRideStatus = findViewById(R.id.rideStatus);
            mCustomerInfo = findViewById(R.id.customerInfo);
            mCustomerName = findViewById(R.id.customerName);
            mCustomerPhone = findViewById(R.id.customerPhone);
            mCustomerDestination = findViewById(R.id.customerDestination);
            mCustomerProfileImage = findViewById(R.id.customerProfileImage);

            isLoggingOut = false;
            /* When the user presses the logout button,
             * The removeLocation method removes the user from the available drivers list.
             * Finally, the user is signed out and returned to the landing page. */
            mLogOut.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            isLoggingOut = true;
                            removeDriverFromAvailableList();

                            FirebaseAuth.getInstance().signOut();

                            Intent intent = new Intent(DriverMapActivity.this, LandingPage.class);
                            startActivity(intent);
                            finish();
                            return;
                        }
                });

            mSettings.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            Intent intent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                            startActivity(intent);
                            return;
                        }
                });

            mRideStatus.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            switch (status)
                                {
                                    case GOING_TO_PICKUP:
                                        status = RIDE_STARTED;
                                        erasePolyLines();
                                        if (destinationLatLng.latitude != 0.0 && destinationLatLng.longitude != 0.0)
                                            getRouteToMarker(destinationLatLng);
                                        mRideStatus.setText("Ride Ended");
                                        break;

                                    case RIDE_STARTED:
                                        recordRideData();
                                        driverEndRide();
                                        break;
                                }
                        }
                });

            getAssignedCustomer();
        }

    /* Driver cancelling a ride should not remove the customerRequest of the customer.
     * Another driver can answer the request then. */
    private void driverEndRide()
        {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                                                        .child("Users")
                                                                        .child("Drivers")
                                                                        .child(userId)
                                                                        .child("customerRequest");
            driverRef.removeValue();

            if (assignedCustomerPickupLocationRef != null)
                assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);

            customerId = "";
            erasePolyLines();
            removeAssignedDriverInfo();

            if (mCustomerMarker != null)
                mCustomerMarker.remove();

            mRideStatus.setText("Ride Started ");
        }

    /* There are three different records of a single ride -- in the customer record,
     * in the driver record and in the history database.
     * add only the ride ID to the customer and driver records.
     * All other secondary details are added to the record for the ride ID in the history database */
    private void recordRideData()
        {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference()
                                                                         .child("History");

            String mostRecentRideId = historyRef.push().getKey();   // Ride's unique key.

            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference()
                                                                        .child("Users")
                                                                        .child("Drivers")
                                                                        .child(userId)
                                                                        .child("history");

            DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference()
                                                                          .child("Users")
                                                                          .child("Customers")
                                                                          .child(customerId)
                                                                          .child("history");

            // Add the ride ID to the customer and driver records.
            driverRef.child(mostRecentRideId).setValue(true);
            customerRef.child(mostRecentRideId).setValue(true);

            HashMap rideData = new HashMap();
            rideData.put("driver", userId);
            rideData.put("customer", customerId);
            rideData.put("driverRating", 0);
            rideData.put("timeStamp", getCurrentTimeStamp());
            rideData.put("destination", destination);
            rideData.put("location/from/lat", pickupLatLng.latitude);
            rideData.put("location/from/lng", pickupLatLng.longitude);
            rideData.put("location/to/lat", destinationLatLng.latitude);
            rideData.put("location/to/lng", destinationLatLng.longitude);
            historyRef.child(mostRecentRideId).updateChildren(rideData);
        }

    private Long getCurrentTimeStamp()
        {
            Long timeStamp = System.currentTimeMillis() / CONVERT_TIME_TO_SECONDS;
            return timeStamp;
        }

    /* The driver needs to be constantly listening to the changes in the reference to a customer
     * To know when a customer has been assigned to him.
     * This is done by checking for a change in the CustomerId child reference of the driver.
     * When a CustomerId is added to a driver (a customer is assigned), finding the location of the
     * customer is next
     * When the CustomerId is removed, the ride is cancelled so the pickup marker is removed */
    private void getAssignedCustomer()
        {
            String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance()
                                                                    .getReference()
                                                                    .child("Users")
                                                                    .child("Drivers")
                                                                    .child(driverId)
                                                                    .child("customerRequest")
                                                                    .child("CustomerId");
            assignedCustomerRef.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists())
                                {
                                    customerId = dataSnapshot.getValue().toString();
                                    getAssignedCustomerPickupLocation();
                                    getAssignedCustomerDestination();
                                    getAssignedCustomerInfo();
                                    status = GOING_TO_PICKUP;
                                }
                            else
                                {
                                    driverEndRide();
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    private void removeAssignedDriverInfo()
        {
            mCustomerInfo.setVisibility(View.GONE);
            mCustomerName.setText("");
            mCustomerPhone.setText("");
            mCustomerDestination.setText("Destination: --");
            mCustomerProfileImage.setImageResource(R.mipmap.ic_profile_image);
        }

    private void getAssignedCustomerDestination()
        {
            String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance()
                                                                    .getReference()
                                                                    .child("Users")
                                                                    .child("Drivers")
                                                                    .child(driverId)
                                                                    .child("customerRequest");
            assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists())
                                {
                                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                                    String destination = dataSnapshot.getValue().toString();
                                    mCustomerDestination.setText("Destination: " + destination);

                                    if (map.get("destination") != null)
                                        {
                                            destination = map.get("destination").toString();
                                            mCustomerDestination.setText("Destination: " + destination);
                                        }
                                    else
                                         mCustomerDestination.setText("Destination: --");

                                    double destinationLat = 0.0;
                                    double destinationLng = 0.0;
                                    if (map.get("destinationLat") != null)
                                        {
                                            destinationLat = Double.valueOf(map.get("destinationLat").toString());
                                        }

                                    if (map.get("destinationLng") != null)
                                    {
                                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                                    }

                                    destinationLatLng = new LatLng(destinationLat, destinationLng);
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    /* Display the assigned user's info in a linear layout at the bottom of the driver's screen.
     * Retrieve the customer info stored as a child of the customer ID and set the respective layout
     * elements to display them. */
    private void getAssignedCustomerInfo()
        {
            DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference()
                                                                                .child("Users")
                                                                                .child("Customers")
                                                                                .child(customerId);
            mCustomerDatabase.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0)
                                {
                                    mCustomerInfo.setVisibility(View.VISIBLE);
                                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                                    if(map.get("name") != null && map.get("phone") != null && map.get("profileImageUri") != null)
                                        {
                                            mCustomerName.setText(map.get("name").toString());
                                            mCustomerPhone.setText(map.get("phone").toString());
                                            Glide.with(getApplication()).load(map.get("profileImageUri").toString()).into(mCustomerProfileImage);
                                        }
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    /* Add an event listener that listens for a change in the longitude and latitude of the customer's position.
     * It listens to the data change at the location the reference is of.
     * The marker for the pickup location is set at the location of the customer assigned. */
    private void getAssignedCustomerPickupLocation()
        {
            Log.i("getassignedLoc", "in get assigned location");
            assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(customerId).child("l");
            assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists() && !customerId.equals(""))
                                {
                                    double locationLat = 0;
                                    double locationLng = 0;
                                    List<Object> map = (List<Object>) dataSnapshot.getValue();

                                    /* Checks if the latitude value in the database is null*/
                                    if (map.get(0) != null && map.get(1) != null)
                                        {
                                            Log.e("0 and 1 are not null", "Working as expected");
                                            locationLat = Double.parseDouble(map.get(0).toString());
                                            locationLng = Double.parseDouble(map.get(1).toString());
                                        }

                                    LatLng pickupLocation = new LatLng(locationLat, locationLng);
                                    pickupLatLng = pickupLocation;

                                    if(mCustomerMarker != null)
                                        mCustomerMarker.remove();

                                    mCustomerMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation)
                                                                                        .title("Pickup Location")
                                                                                        .icon(BitmapDescriptorFactory
                                                                                        .fromResource(R.mipmap.ic_driver_car)));

                                    getRouteToMarker(pickupLocation);
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    private void getRouteToMarker(LatLng pickupLocation)
        {
            Routing routing = new Routing.Builder()
                                         .travelMode(AbstractRouting.TravelMode.DRIVING)
                                         .withListener(this)
                                         .alternativeRoutes(false)
                                         .waypoints(new LatLng(mlastLocation.getLatitude(), mlastLocation.getLongitude()), pickupLocation)
                                         .build();
            routing.execute();
        }

    @Override
    public void onMapReady(GoogleMap googleMap)
        {
            mMap = googleMap;

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    return;
                }
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

    /* Sets the mGoogleApiClient variable declared at the top of the page. */
    protected synchronized void buildGoogleApiClient()
        {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                                                  .addConnectionCallbacks(this)
                                                  .addOnConnectionFailedListener(this)
                                                  .addApi(LocationServices.API)
                                                  .build();
            mGoogleApiClient.connect();
        }

    /* When the user moves, the position on the map should be updated as well.
     * Send the current location to the database using GeoFire.
     * Update the location stored for a driver in the driverAvailable record. */
    @Override
    public void onLocationChanged(Location location)
        {
            mlastLocation = location;
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            mMap.addMarker(new MarkerOptions().position(latLng)
                                              .title("Me!")
                                              .icon(BitmapDescriptorFactory
                                              .fromResource(R.mipmap.ic_driver)));

            DatabaseReference driversAvailableRef = FirebaseDatabase.getInstance().getReference("driversAvailable");
            GeoFire geoFireForDriversAvailable = new GeoFire(driversAvailableRef);

            DatabaseReference driversWorkingRef =  FirebaseDatabase.getInstance().getReference("driversWorking");
            GeoFire geoFireForDriversWorking = new GeoFire(driversWorkingRef);

            switch (customerId)
                {
                    case "":
                        geoFireForDriversAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                        geoFireForDriversWorking.removeLocation(userId);
                        break;

                    default:
                        geoFireForDriversWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                        geoFireForDriversAvailable.removeLocation(userId);
                        break;

                }
        }


    /* When the map activity is loaded and everything is ready.
    *  Makes requests every second. */
    @Override
    public void onConnected(@Nullable Bundle bundle)
        {
            mlocationRequest = new LocationRequest();
            mlocationRequest.setInterval(1000); // In milliseconds
            mlocationRequest.setFastestInterval(1000);
            mlocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //drains the battery!!

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(DriverMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
                }
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mlocationRequest, this);
        }

    @Override
    public void onConnectionSuspended(int i)
        {

        }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
        {

        }

    /* Remove the driver from the list of available drivers when the app is killed. */
    @Override
    protected void onStop()
        {
            super.onStop();
            if (!isLoggingOut)
            {
                removeDriverFromAvailableList();
            }
        }

    /* Removes a driver from driverAvailable database.
     * It removes the location listener to avaoid crashes when the driver logs out. */
    private void removeDriverFromAvailableList()
        {
            if (isLoggingOut)
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference driversAvailableRef = FirebaseDatabase.getInstance().getReference("driversAvailable");
            GeoFire geoFireForDriversAvailable = new GeoFire(driversAvailableRef);

            DatabaseReference driversWorkingRef =  FirebaseDatabase.getInstance().getReference("driversWorking");
            GeoFire geoFireForDriversWorking = new GeoFire(driversWorkingRef);

            if (!customerId.equals(""))
            {
                geoFireForDriversWorking.removeLocation(userId);
            }

            // Default
            geoFireForDriversAvailable.removeLocation(userId);
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            switch (requestCode) {
                case REQUEST_LOCATION_CODE:
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                        {
                            mapFragment.getMapAsync(this);
                        }
                    else
                        {
                            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                        }
                    break;
                }
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
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex)
        {
            if(polylines.size()>0) {
                for (Polyline poly : polylines) {
                    poly.remove();
                }
            }

            polylines = new ArrayList<>();
            //add route(s) to the map.
            for (int i = 0; i <route.size(); i++) {

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
    public void onRoutingCancelled() {

    }
}
