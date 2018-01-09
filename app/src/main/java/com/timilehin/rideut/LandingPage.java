package com.timilehin.rideut;

import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LandingPage extends AppCompatActivity
    {
        private Button driverButton, customerButton;
        @Override
        protected void onCreate(Bundle savedInstanceState)
            {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_landing_page);

                driverButton = (Button) findViewById(R.id.driverButton);
                customerButton = (Button) findViewById(R.id.riderButton);

                driverButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                            {
                                Intent intent = new Intent(LandingPage.this, DriverLoginActivity.class);
                                startActivity(intent);
                                finish();
                                return;
                            }
                    });

                customerButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                            {
                                Intent intent = new Intent(LandingPage.this, CustomerLoginActivity.class);
                                startActivity(intent);
                                finish();
                                return;
                            }
                    });
            }



    }
