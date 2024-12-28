package com.example.appointment;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class MyAppointmentsActivity extends AppCompatActivity {

    private ListView appointmentsListView;
    private Button btnLogout;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private ArrayAdapter<String> adapter;
    private List<String> appointmentsList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_appointments);

        // Find UI elements
        appointmentsListView = findViewById(R.id.appointmentsListView);
        btnLogout = findViewById(R.id.btnLogout);

        // Initialize Firebase Auth and Firestore instances
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Initialize the appointments list and adapter
        appointmentsList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, appointmentsList);
        appointmentsListView.setAdapter(adapter);

        // Fetch appointments from Firestore
        fetchAppointments();

        // Set up the logout button click listener
        btnLogout.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(MyAppointmentsActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void fetchAppointments() {
        // Get the current logged-in user
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            // Query Firestore for appointments with the user's ID
            firestore.collection("appointments")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot querySnapshot = task.getResult();
                            if (querySnapshot != null) {
                                // Clear the current appointments list
                                appointmentsList.clear();
                                // Iterate over each document and add the appointment date to the list
                                for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                                    String formattedDate = document.getString("formattedDate");
                                    if (formattedDate != null) {
                                        appointmentsList.add("Date: " + formattedDate);
                                    }
                                }
                                // Notify the adapter of data changes
                                adapter.notifyDataSetChanged();
                            } else {
                                Toast.makeText(MyAppointmentsActivity.this, "No appointments found", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MyAppointmentsActivity.this, "Error getting appointments: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
        }
    }
}
