package com.timilehin.rideut;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mlastLocation;
    private LocationRequest mlocationRequest;

    private boolean driverFound = false;
    private boolean requestOn = false;
    private boolean failed = false;
    private String assignedDriverID;
    private LatLng pickupLocation;
    private String destination;
    private Marker mDriverMarker, mPickupMarker;

    private LinearLayout mDriverInfo;
    private ImageView mDriverProfileImage;
    private TextView mDriverName, mDriverPhone, mDriverCar;

    private GeoQuery geoQuery;
    private DatabaseReference driverLocationRef;
    private ValueEventListener mDriverLocationRefListener;

    private SupportMapFragment mapFragment;

    private LatLng destinationLatLng;
    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;

    private RadioGroup mRadioGroup;
    private String mRequestService;
    private Button mLogOut, mRequestRide, mSettings, mHistory;

    private int radius = 2;
    private boolean isLoggingOut;
    private static final int REQUEST_LOCATION_CODE = 99;

    @Override
    protected void onCreate(Bundle savedInstanceState)
        {
            requestOn = false;
            isLoggingOut = false;

            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_customer_map);
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            mapFragment = (SupportMapFragment) getSupportFragmentManager()
                                                .findFragmentById(R.id.map);

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
                }
            else
                {
                    mapFragment.getMapAsync(this);
                }

            // Default LatLng.
            destinationLatLng = new LatLng(0.0, 0.0);

            // Buttons.
            mLogOut = findViewById(R.id.logout);
            mHistory = findViewById(R.id.history);
            mSettings = findViewById(R.id.Settings);
            mRequestRide = findViewById(R.id.requestRide);

            // EditTexts and Image.
            mDriverCar = findViewById(R.id.driverCar);
            mDriverInfo = findViewById(R.id.driverInfo);
            mDriverName = findViewById(R.id.driverName);
            mDriverPhone = findViewById(R.id.driverPhone);
            mDriverProfileImage = findViewById(R.id.driverProfileImage);

            /* Driver Service the customer wants
             * with car as the default. */
            mRadioGroup = findViewById(R.id.radioGroup);
            mRadioGroup.check(R.id.car);

            Log.e("mLogOut", "Set Up almost complete");

            /* When the customer presses the logout button,
             * The removeLocation method removes the customer's request from customerRequest database if one exists.
             * Finally, the user is signed out and returned to the landing page. */
            mLogOut.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            isLoggingOut = true;

                            Log.e("mLogOut", "Log out button pressed.");

                            removeOrAddCustomerRequest(FirebaseAuth.getInstance().getCurrentUser().getUid(), false, mlastLocation);
                            removeReferenceFromDriverRecordAndReset();

                            FirebaseAuth.getInstance().signOut();

                            Log.e("mLogOut", "Sign out was successful too.");

                            Intent intent = new Intent(CustomerMapActivity.this, LandingPage.class);
                            startActivity(intent);
                            finish();
                            return;
                        }
                });

            /*  Create reference to the database that stores the requests.
             *  Use setLocation to add the request to the database.
             *  Add a marker at the customer's position at the time of making the request.
             *  Notify the customer that we are assigning a driver.
             *
             *  If a driver is already assigned, then we cancel the ride and remove the reference to
             *  the customer from the driver's record and remove the listeners on the reference record.
             *  Flow Control variables are reset as well and the pending/accepted requests are removed
             *  from the customerRequest database. */
            mRequestRide.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            Log.e("CustomerMapActivity", "Got the userId");

                            if (requestOn || failed)
                                {
                                    endRide();
                                }
                            else
                                {
                                    Log.e("Not in Car", "I seem to be in the ride request area still...");
                                    int selectedId = mRadioGroup.getCheckedRadioButtonId();
                                    final RadioButton radioButton = (RadioButton) findViewById(selectedId);

                                    /* Driver hasn't chosen any service yet so prevent the info from saving. */
                                    if (radioButton.getText() == null)
                                        return;

                                    mRequestService = radioButton.getText().toString();

                                    removeOrAddCustomerRequest(userId, true, mlastLocation);
                                    pickupLocation = new LatLng(mlastLocation.getLatitude(), mlastLocation.getLongitude());
                                    mPickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation)
                                                                                      .title("Pickup")
                                                                                      .icon(BitmapDescriptorFactory
                                                                                      .fromResource(R.mipmap.ic_pickup_location)));
                                    mRequestRide.setText("Finding a driver for you...");
                                    requestOn = true;
                                    getClosestDriver();
                                }
                        }
                });

            mSettings.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                            startActivity(intent);
                            return;
                        }
                });

            mHistory.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            Intent intent = new Intent(CustomerMapActivity.this, HistoryActivity.class);
                            intent.putExtra("UserMode", "Customers");
                            startActivity(intent);
                            return;
                        }
                });

            /* Code for the Google API that helps a customer choose a destination using google place autocomplete. */
            PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener()
                {
                    @Override
                    public void onPlaceSelected(Place place)
                        {
                            // TODO: Get info about the selected place.
                            destination = place.getName().toString();
                            destinationLatLng = place.getLatLng();
                        }

                    @Override
                    public void onError(Status status)
                        {
                            // TODO: Handle the error.
                        }
                });
        }

    /* Helper method to find the closest available driver to the customer.
     * The customer's info is then transmitted to the driver
     * The driver will now appear on the map of the customer.
     * This doesn't account for the fact that the driver may refuse the ride before telling the showing up to the customer
     * Creates the query to find an available driver within the specified radius with pickupLocation as the center. */
    private void getClosestDriver()
        {
            final DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference("driversAvailable");
            GeoFire geoFire = new GeoFire(driverLocation);

            geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
            geoQuery.removeAllListeners(); // Remove all listeners before creating a new one to prevent ghost events.

            geoQuery.addGeoQueryEventListener(new GeoQueryEventListener()
                {
                    /* Called when a driver is chosen from the available ones within the specified radius. */
                    @Override
                    public void onKeyEntered(String key, GeoLocation location)
                        {
                            /* DriverFound is checked to ensure once a driver is found, this method isn't invoked again.
                            *  This portion of code is mostly to tell the driver the customer he is about to pick.
                            *  When the customer's info is sent to the driver, the driver is then shown on the map. */
                            if (!driverFound && requestOn)
                                {
                                    DatabaseReference mDriverServiceRef = FirebaseDatabase.getInstance()
                                                                                          .getReference()
                                                                                          .child("Users")
                                                                                          .child("Drivers")
                                                                                          .child(key);

                                    mDriverServiceRef.addListenerForSingleValueEvent(new ValueEventListener()
                                        {
                                            @Override
                                            public void onDataChange(DataSnapshot dataSnapshot)
                                                {
                                                    if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0)
                                                        {
                                                            if (driverFound)
                                                                return;

                                                            Log.e("onKeyEntered", "I am still looking for a driver...");

                                                            Map<String, Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                                                            if(driverMap.get("driverService").equals(mRequestService))
                                                                {
                                                                    driverFound = true;
                                                                    assignedDriverID = dataSnapshot.getKey();

                                                                    String customerId = FirebaseAuth.getInstance().getCurrentUser()
                                                                                                                  .getUid();

                                                                    DatabaseReference driverRef = FirebaseDatabase.getInstance()
                                                                                                                  .getReference()
                                                                                                                  .child("Users")
                                                                                                                  .child("Drivers")
                                                                                                                  .child(assignedDriverID)
                                                                                                                  .child("customerRequest");

                                                                    HashMap map = new HashMap();
                                                                    map.put("CustomerId", customerId);
                                                                    map.put("destination", destination);
                                                                    map.put("destinationLat", destinationLatLng.latitude);
                                                                    map.put("destinationLng", destinationLatLng.longitude);
                                                                    driverRef.updateChildren(map);

                                                                    getDriverLocationOnMap();
                                                                    getAssignedDriverInfo();
                                                                    getHasRideEnded();
                                                                    mRequestRide.setText("Found Driver!");
                                                                    Log.e("onDataChange", "found a driver...");
                                                                }
                                                        }
                                                }

                                            @Override
                                            public void onCancelled(DatabaseError databaseError)
                                                {

                                                }
                                        });
                                }
                            else
                                failed = true;
                        }

                    @Override
                    public void onKeyExited(String key)
                        {

                        }

                    @Override
                    public void onKeyMoved(String key, GeoLocation location)
                        {

                        }

                    /* Called when all the drivers within the radius have been found. */
                    @Override
                    public void onGeoQueryReady()
                        {
                            /* Find drivers in an expanded radius. */
                            if (!driverFound)
                                {
                                    radius++;
                                    Log.e("onGeoQueryReady", "I am trying to increase the radius still...");
                                    Log.i("onGeoQueryReady: ", radius + "");
                                    getClosestDriver(); // Recursive call to find a driver in the expanded radius.
                                }
                        }

                    @Override
                    public void onGeoQueryError(DatabaseError error)
                        {
                            Log.e("onGeoQueryError", "Oops, something happened...");
                            return;
                        }
                });
        }

    /* Monitors the driver's record for a change in the data.
     * If there is a change that signifies the ride has been stopped by the driver, endRide() is called. */
    private void getHasRideEnded()
        {
            driveHasEndedRef = FirebaseDatabase.getInstance()
                                               .getReference()
                                               .child("Users")
                                               .child("Drivers")
                                               .child(assignedDriverID)
                                               .child("customerRequest")
                                               .child("CustomerId");
            driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists())
                                {

                                }
                            else
                                {
                                    endRide();
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    private void getAssignedDriverInfo()
        {
            DatabaseReference assignedDriverRef = FirebaseDatabase.getInstance()
                                                                  .getReference()
                                                                  .child("Users")
                                                                  .child("Drivers")
                                                                  .child(assignedDriverID);

            assignedDriverRef.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            Log.e("Driver Info Check", "There was a change in data.");

                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0)
                                {
                                    Log.e("Driver Info Check", "the datasnapshot exists");
                                    if (requestOn)
                                        mDriverInfo.setVisibility(View.VISIBLE);
                                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                                    if(map.get("name") != null && map.get("phone") != null && map.get("car") != null)
                                        {
                                            mDriverName.setText(map.get("name").toString());
                                            mDriverPhone.setText(map.get("phone").toString());
                                            mDriverCar.setText(map.get("car").toString());
                                            if (map.get("profileImageUri") != null)
                                                Glide.with(getApplication()).load(map.get("profileImageUri").toString()).into(mDriverProfileImage);
                                        }
                                }
                            Log.e("Driver Info Check", "getting the driver's info is complete...");
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
        }

    /* Helper method to find the driver's location from the database and show it on the customer's map.
     * Reference has l as a child because GeoFire stores the location under l.
     * Child 'l' has 0: latitude and 1: longitude */
    private void getDriverLocationOnMap()
        {
            driverLocationRef = FirebaseDatabase.getInstance()
                                                .getReference()
                                                .child("driversAvailable")
                                                .child(assignedDriverID)
                                                .child("l");
            mDriverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener()
                {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot)
                        {
                            if (dataSnapshot.exists() && requestOn)
                                {
                                    double locationLat = 0;
                                    double locationLng = 0;
                                    List<Object> map = (List<Object>) dataSnapshot.getValue();

                                    /* We want only 1 marker for a driver so remove existing markers when update comes in.
                                     * When the existing markers have been removed, add the new one. */
                                    if (mDriverMarker != null)
                                        mDriverMarker.remove();

                                    /* Checks if the latitude value in the database is null */
                                    if (map.get(0) != null && map.get(1) != null)
                                        {
                                            locationLat = Double.parseDouble(map.get(0).toString());
                                            locationLng = Double.parseDouble(map.get(1).toString());
                                        }

                                    LatLng driverLocation = new LatLng(locationLat, locationLng);

                                    Location loc1 = new Location("");
                                    loc1.setLatitude(driverLocation.latitude);
                                    loc1.setLongitude(driverLocation.longitude);

                                    Location loc2 = new Location("");
                                    loc2.setLatitude(pickupLocation.latitude);
                                    loc2.setLongitude(pickupLocation.longitude);

                                    float distance = loc2.distanceTo(loc1);

                                    if (distance < 50)
                                        {
                                            Toast.makeText(CustomerMapActivity.this, "Your driver is here", Toast.LENGTH_SHORT).show();
                                            mRequestRide.setText("Your driver is here!");

                                        }
                                    else
                                        {
                                            mRequestRide.setText("Your driver is " + distance + " away");
                                        }

                                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation)
                                                                                      .title("Your Driver")
                                                                                      .icon(BitmapDescriptorFactory
                                                                                      .fromResource(R.mipmap.ic_driver)));
                                }
                        }

                    @Override
                    public void onCancelled(DatabaseError databaseError)
                        {

                        }
                });
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
    protected void buildGoogleApiClient()
        {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                                                  .addConnectionCallbacks(this)
                                                  .addOnConnectionFailedListener(this)
                                                  .addApi(LocationServices.API)
                                                  .build();
            mGoogleApiClient.connect();
        }

    /* When the user moves, the position on the map should be updated as well.
     * Send the current location to the database. */
    @Override
    public void onLocationChanged(Location location)
        {
            mlastLocation = location;
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            if (requestOn)
                {
                    removeOrAddCustomerRequest(userId, true, location);
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
                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
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
        }

    @Override
    protected void onDestroy()
        {
            super.onDestroy();
        }

    /* Helper method for cancelling ride when a user presses the requestRide again during a ride. */
    private void endRide()
        {
            Log.e("Not in Car", "I have cancelled an ongoing ride...");
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            geoQuery.removeAllListeners();

            if (driveHasEndedRef != null)
                driveHasEndedRef.removeEventListener(driveHasEndedRefListener);

            if (driverLocationRef != null)
                driverLocationRef.removeEventListener(mDriverLocationRefListener);

            removeOrAddCustomerRequest(userId, false, mlastLocation);
            removeReferenceFromDriverRecordAndReset();

            if (mPickupMarker != null)
                mPickupMarker.remove();

            if (mDriverMarker != null)
                mDriverMarker.remove();

            removeAssignedDriverInfo();
            mRequestRide.setText("REQUEST RIDE");
            radius = 1;
            requestOn = false;
            failed = false;
        }

    private void removeAssignedDriverInfo()
        {
            mDriverInfo.setVisibility(View.GONE);
            mDriverName.setText("");
            mDriverPhone.setText("");
            mDriverCar.setText("");
            mDriverProfileImage.setImageResource(R.mipmap.ic_profile_image);
        }

    /* Add or remove the customer based on the value of the second parameter.
     * If true, the customer request is added to customerRequest database.
     * If false, the customer request is removed from the customerRequest database. */
    private void removeOrAddCustomerRequest(String userId, boolean createNew, Location location)
        {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
            GeoFire geoFire = new GeoFire(ref);

            if (createNew)
                geoFire.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
            else
                {
                    if (isLoggingOut)
                        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

                    geoFire.removeLocation(userId);
                }
            Log.e("Report", "removed or added customerRequest!");
        }

    /* Helper method to remove the customer's ID from the driver record when the customer cancels a ride.
     * Reset the variables used for tracking the ride. */
    private void removeReferenceFromDriverRecordAndReset()
        {
            radius = 1;
            requestOn = false;
            driverFound = false;

            if (assignedDriverID != null)
                {
                    DatabaseReference driverRef = FirebaseDatabase.getInstance()
                                                                  .getReference()
                                                                  .child("Users")
                                                                  .child("Drivers")
                                                                  .child(assignedDriverID)
                                                                  .child("customerRequest");
                    driverRef.setValue(true);
                    assignedDriverID = null;
                }
            Log.e("Report", "removed the reference from the driver record!");
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
}

