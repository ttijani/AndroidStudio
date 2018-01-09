package com.timilehin.rideut;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CustomerLoginActivity extends AppCompatActivity {

    // Used for the firebase authorization process.
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;
    // Used to communicate with the elements on the activity.
    private Button driverLogin, driverRegister;
    private EditText loginText, registerText;
    @Override
    protected void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_driver_login);

            //Get the firebase instance
            mAuth = FirebaseAuth.getInstance();
            firebaseAuthListener = new FirebaseAuth.AuthStateListener()
                {
                    // Called whenever a user state changes i.e. logs in or out.
                    @Override
                    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth)
                        {
                            // Gets the current user info
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null)
                                {
                                    Intent intent = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                                    startActivity(intent);
                                    finish();
                                    return;
                                }
                        }
                };

            driverLogin = (Button) findViewById(R.id.dLogin);
            driverRegister = (Button) findViewById(R.id.dRegister);
            loginText = (EditText) findViewById(R.id.driverName);
            registerText = (EditText) findViewById(R.id.password);

            driverRegister.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            final String userName = loginText.getText().toString().trim();
                            final String password = registerText.getText().toString().trim();
                            mAuth.createUserWithEmailAndPassword(userName, password).addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>()
                                {
                                    @Override
                                    public void onComplete(@NonNull Task<AuthResult> task)
                                        {
                                            if (!task.isSuccessful())
                                                {
                                                    Toast.makeText(CustomerLoginActivity.this, "Registration Failed", Toast.LENGTH_LONG).show();
                                                }
                                            else
                                                {
                                                    String user_id = mAuth.getUid();
                                                    DatabaseReference current_user_db = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(user_id);
                                                    current_user_db.setValue(true);
                                                }
                                        }
                                });
                        }
                });

            driverLogin.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                        {
                            final String userName = loginText.getText().toString().trim();
                            final String password = registerText.getText().toString().trim();
                            mAuth.signInWithEmailAndPassword(userName, password)
                                    .addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>()
                                    {
                                        @Override
                                        public void onComplete(@NonNull Task<AuthResult> task)
                                        {
                                            if (!task.isSuccessful())
                                                {
                                                    Toast.makeText(CustomerLoginActivity.this, "Log In Failed", Toast.LENGTH_SHORT).show();
                                                }
                                        }
                                    });
                        }
                });

        }

    @Override
    protected void onStart()
        {
            super.onStart();
            mAuth.addAuthStateListener(firebaseAuthListener);
        }

    @Override
    protected void onStop()
        {
            super.onStop();
            mAuth.removeAuthStateListener(firebaseAuthListener);
        }
}
