package com.timilehin.rideut;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DriverSettingsActivity extends AppCompatActivity
    {
        private Button mConfirm, mBack;
        private EditText mName, mPhoneNum, mCar;
        private ImageView mProfileImage;

        private FirebaseAuth mAuth;
        private DatabaseReference mDriverDatabase;

        private String userName, phoneNumber, mProfileImgUri, car;
        private RadioGroup mRadioGroup;
        private String mDriverService;

        private String userId;
        private Uri resultUri;

        private final int IMAGE_QUALITY = 20;
        private final int GALLERY_INTENT_ID = 1;

        @Override
        protected void onCreate(Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_driver_settings);

                mName = findViewById(R.id.driverName);
                mPhoneNum = findViewById(R.id.driverPhoneNumber);
                mCar = findViewById(R.id.driverCar);

                mConfirm = findViewById(R.id.confirmDriverInfo);
                mBack = findViewById(R.id.backToDriverMap);

                mProfileImage = findViewById(R.id.driverProfileImage);

                mRadioGroup = findViewById(R.id.radioGroup);

                mAuth = FirebaseAuth.getInstance();
                userId = mAuth.getCurrentUser().getUid();
                mDriverDatabase = FirebaseDatabase.getInstance().getReference()
                                                                .child("Users")
                                                                .child("Drivers")
                                                                .child(userId);
                getUserInfo(); // Must be after the database is defined.

                mConfirm.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                            {
                                saveUserInformation();
                            }
                    });

                mBack.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                            {
                                finish();
                                return;
                            }
                    });

                /* When the icon is clicked, give the options of the apps to fulfill the intent.
                 * GALLERY_INTENT_ID is to know the activity that has the result in the OnActivityResult() method. */
                mProfileImage.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                            {
                                Intent intent = new Intent(Intent.ACTION_PICK);
                                intent.setType("image/*");
                                startActivityForResult(intent, GALLERY_INTENT_ID);
                            }
                    });
            }

        /* Saves additional user information like username, phone number and a profile image.
         * All these information are added as children of the customer ID on the database. */
        private void saveUserInformation()
            {
                userName = mName.getText().toString().trim();
                phoneNumber = mPhoneNum.getText().toString().trim();
                car = mCar.getText().toString().trim();

                int selectedId = mRadioGroup.getCheckedRadioButtonId();
                final RadioButton radioButton = (RadioButton) findViewById(selectedId);

                /* Driver hasn't chosen any service yet so prevent the info from saving. */
                if (radioButton.getText() == null)
                    return;

                mDriverService = radioButton.getText().toString();

                HashMap userInfo = new HashMap();
                userInfo.put("name", userName);
                userInfo.put("phone", phoneNumber);
                userInfo.put("car", car);
                userInfo.put("driverService", mDriverService);
                mDriverDatabase.updateChildren(userInfo);

                /* Code segment for uploading the user's profile image to the Firebase storage and
                 * adding a reference in the record of the customer with userID. */
                if (resultUri != null)
                    {
                        StorageReference filePath = FirebaseStorage.getInstance().getReference()
                                                                                 .child("profile_images")
                                                                                 .child("userId");
                        /* Convert the image into a bitmap format. */
                        Bitmap bitmap = null;
                        try
                            {
                                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), resultUri);
                            }
                        catch (IOException e)
                            {
                                e.printStackTrace();
                            }

                        /* Stuff to compress the bitmap variable for easy upload to the Firebase storage */
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, baos);
                        byte[] imageData = baos.toByteArray();
                        UploadTask uploadTask = filePath.putBytes(imageData); // Upload to the Firebase storage.

                        /* When an image is successfully added to the Firebase storage, the image url
                         * can now be added to the user's database record.
                         * When complete, return to the CustomerMapActivity. */
                        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>()
                            {
                                @Override
                                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot)
                                    {
                                        Uri downloadUri = taskSnapshot.getDownloadUrl();
                                        Map newImage = new HashMap();
                                        newImage.put("profileImageUri", downloadUri.toString());
                                        mDriverDatabase.updateChildren(newImage);
                                        finish();
                                        return;
                                    }
                            });
                        /* On failure, return to the CustomerActivityMap. */
                        uploadTask.addOnFailureListener(new OnFailureListener()
                            {
                                @Override
                                public void onFailure(@NonNull Exception e)
                                    {
                                        Toast.makeText(DriverSettingsActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                                        finish();
                                        return;
                                    }
                            });
                    }
                else
                    {
                        finish();
                    }
            }

        /* It AUTO FILLS the name and the phone number in their respective name EditText boxes.
         * Helper method to display the user information that has already been provided.
         * The user profile image is displayed in the ImageView using Glide. */
        private void getUserInfo()
            {
                mDriverDatabase.addValueEventListener(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0)
                                    {
                                        Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                                        if(map.get("name") != null && map.get("phone") != null && map.get("car") != null)
                                            {
                                                userName = map.get("name").toString();
                                                mName.setText(userName);
                                                phoneNumber = map.get("phone").toString();
                                                mPhoneNum.setText(phoneNumber);
                                                car = map.get("car").toString();
                                                mCar.setText("Vehicle is a: " + car);

                                                if (map.get("profileImageUri") != null)
                                                    {
                                                        mProfileImgUri = map.get("profileImageUri").toString();
                                                        Glide.with(getApplication()).load(mProfileImgUri).into(mProfileImage);
                                                    }

                                                if (map.get("driverService") != null)
                                                    {
                                                        mDriverService = map.get("driverService").toString();
                                                        switch (mDriverService)
                                                            {
                                                                case "Moped":
                                                                    mRadioGroup.check(R.id.moped);
                                                                    break;

                                                                case "Car":
                                                                    mRadioGroup.check(R.id.car);
                                                                    break;
                                                            }

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

        /* When the activity we started in the mProfileImage onClickListener is completed, this
         * method is called (since we used startActivityForResult().)
         * We check if the activity that is completed successfully is for the mProfileImage.
         * Then we get the url returned from the activity and set the image url to the returned url.
         * -----------------------------------------------------------------------------------------
         * Intent returns the url of the image chosen from the gallery --> data.getData() is the url. */
        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data)
            {
                super.onActivityResult(requestCode, resultCode, data);
                if (requestCode == GALLERY_INTENT_ID && resultCode == AppCompatActivity.RESULT_OK)
                    {
                        final Uri imageUri = data.getData();
                        resultUri = imageUri;
                        mProfileImage.setImageURI(resultUri);
                    }
            }
    }
