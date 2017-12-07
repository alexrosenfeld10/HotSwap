package com.teamcaffeine.hotswap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.timessquare.CalendarPickerView;
import com.teamcaffeine.hotswap.R;
import com.teamcaffeine.hotswap.swap.Item;

import java.io.Serializable;
import java.util.ArrayList;
import java.io.IOException;
import java.util.List;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ListItemActivity extends AppCompatActivity {

    private String TAG = "ListItemActivity";

    private FirebaseUser firebaseUser;
    private FirebaseDatabase database;
    private DatabaseReference items;
    private DatabaseReference geoFireRef;
    private String itemTable = "items";
    private String geoFireTable = "items_location";

    private EditText editItemName;
    private EditText editTags;
    private EditText editPrice;
    private EditText editDescription;
    private EditText editAddress;
    private Button listItemButton;
    private CalendarPickerView calendar;
    private List<String> itemList = new ArrayList<String>();

    private int RESULT_ERROR = 88;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_list_item);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        database = FirebaseDatabase.getInstance();
        items = database.getReference(itemTable);
        geoFireRef = database.getReference(geoFireTable);

        editItemName = (EditText) findViewById(R.id.editItemName);
        editTags = (EditText) findViewById(R.id.editTags);
        editPrice = (EditText) findViewById(R.id.editPrice);
        editDescription = (EditText) findViewById(R.id.editDescription);
        editAddress = (EditText) findViewById(R.id.editAddress);
        calendar = (CalendarPickerView) findViewById(R.id.calendarView);
        listItemButton = (Button) findViewById(R.id.listItemButton);

        // get the  current list of the user's items from the intent
        Bundle extras = getIntent().getExtras();
        itemList = extras.getStringArrayList("itemList");

        Intent intent = getIntent();
        Bundle args = intent.getBundleExtra("BUNDLE");
        itemList = (ArrayList<String>) args.getSerializable("itemList");

        Calendar nextYear = Calendar.getInstance();
        nextYear.add(Calendar.YEAR, 1);

        Date today = new Date();
        calendar.init(today, nextYear.getTime())
                .withSelectedDate(today)
                .inMode(CalendarPickerView.SelectionMode.MULTIPLE);

        calendar.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        // Disallow ScrollView to intercept touch events.
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;

                    case MotionEvent.ACTION_UP:
                        // Allow ScrollView to intercept touch events.
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }

                v.onTouchEvent(event);
                return true;
            }
        });

        listItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String itemID = items.push().getKey();
                String itemName = editItemName.getText().toString();
                String itemAddress = editAddress.getText().toString();
                String itemPrice = editPrice.getText().toString();
                String itemDescription = editDescription.getText().toString();

                Item newItem = new Item(itemID, itemName, firebaseUser.getUid(), itemDescription, itemPrice, itemAddress);

                // DATA VALIDATION
                // a user cannot list 2 items with the same name
                // if they enter a new item with the same name as an existing item, an Toast
                // will show with an error message
                if (itemList.contains(itemName)) {
                    Toast.makeText(getBaseContext(), R.string.duplicate_item, Toast.LENGTH_LONG).show();
                } else {
                    // if the user is adding a new item with a new name, submit it to the database
                    submit(newItem);

                    // create an intent to send back to the HomeActivity
                    Intent i = new Intent();

                    // send the updated itemList back to the Home Fragment
                    i.putExtra("newItem", itemName);

                    // Set the result to indicate adding the item was successful
                    // and finish the activity
                    setResult(Activity.RESULT_OK, i);
                    finish();
                }
            }
        });
    }

    public LatLng getLocationFromAddress(String strAddress)
    {
        //Create coder with Activity context - this
        Geocoder coder = new Geocoder(ListItemActivity.this);
        List<Address> address;
        try {
            //Get latLng from String
            address = coder.getFromLocationName(strAddress,5);

            //check for null
            if (address == null) {
                return null;
            }

            //Lets take first possibility from the all possibilities.
            Address location=address.get(0);
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            return latLng;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void submit(Item item) {
        Log.i(TAG, "submit method");
        final Item newItem = item;
        final String itemID = newItem.getItemID();
        final String itemName = newItem.getName();
        final String itemAddress = newItem.getAddress();

        items.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.i(TAG, "onDataChange method");
                Map<String, Object> itemUpdate = new HashMap<>();
                itemUpdate.put(itemID, newItem.toMap());
                Log.i(TAG, "item added to database");

                GeoFire geoFire = new GeoFire(geoFireRef);
                LatLng itemLatLng = getLocationFromAddress(itemAddress);
                if (itemLatLng != null) {
                    items.updateChildren(itemUpdate);
                    geoFire.setLocation(itemID, new GeoLocation(itemLatLng.latitude, itemLatLng.longitude));
                    Log.i(TAG, "address found");
                } else {
                    // TODO: handle invalid address / location data more gracefully - likely when we put the address fragment here
                    Toast.makeText(getBaseContext(), R.string.unable_to_add_address, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("ListItemFragment", "The read failed: " + databaseError.getCode());
            }
        });
    }
}
