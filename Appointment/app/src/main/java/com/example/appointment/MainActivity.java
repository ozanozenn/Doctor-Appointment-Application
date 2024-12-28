package com.example.appointment;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private Button datePickerButton;
    private Button timePickerButton;
    private Button confirmButton;
    private Button btnAppointment;
    private Button btnLogout;
    private Calendar selectedDate;
    private boolean isDateSelected = false;
    private boolean isTimeSelected = false;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find UI elements
        datePickerButton = findViewById(R.id.datePickerButton);
        timePickerButton = findViewById(R.id.timePickerButton);
        confirmButton = findViewById(R.id.confirmButton);
        btnAppointment = findViewById(R.id.btnAppointment);
        btnLogout = findViewById(R.id.btnLogout);
        selectedDate = Calendar.getInstance();

        // Get Firebase Auth and Firestore instances
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // DatePicker button click listener
        datePickerButton.setOnClickListener(v -> {
            int year = selectedDate.get(Calendar.YEAR);
            int month = selectedDate.get(Calendar.MONTH);
            int day = selectedDate.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                    (view, year1, month1, dayOfMonth) -> {
                        selectedDate.set(Calendar.YEAR, year1);
                        selectedDate.set(Calendar.MONTH, month1);
                        selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                        // Check if the selected date is a weekend
                        int dayOfWeek = selectedDate.get(Calendar.DAY_OF_WEEK);
                        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                            // Notify the user if the selected date is a weekend
                            Toast.makeText(MainActivity.this, "Please select only weekdays.", Toast.LENGTH_SHORT).show();
                            isDateSelected = false;
                        } else if (selectedDate.getTimeInMillis() < System.currentTimeMillis()) {
                            // Notify the user if the selected date is in the past
                            Toast.makeText(MainActivity.this, "You cannot select a past date.", Toast.LENGTH_SHORT).show();
                            isDateSelected = false;
                        } else {
                            // Confirm the selected date if it is valid
                            Toast.makeText(MainActivity.this, "Selected date: " + dayOfMonth + "/" + (month1 + 1) + "/" + year1, Toast.LENGTH_SHORT).show();
                            isDateSelected = true;
                        }
                    }, year, month, day);

            // Prevent the user from selecting past dates
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);

            // Show the DatePickerDialog
            datePickerDialog.show();
        });

        // TimePicker button click listener
        timePickerButton.setOnClickListener(v -> {
            if (!isDateSelected) {
                Toast.makeText(MainActivity.this, "Please select a date first.", Toast.LENGTH_SHORT).show();
                return;
            }

            fetchAppointmentsForDate(selectedDate, new OnFetchAppointmentsListener() {
                @Override
                public void onSuccess(QuerySnapshot appointments) {
                    // Generate time slots for the user to select
                    String[] timeSlots = generateTimeSlots();
                    List<String> availableTimeSlots = new ArrayList<>();

                    // Fetch existing appointments and identify unavailable time slots
                    List<Calendar> unavailableTimes = new ArrayList<>();
                    for (DocumentSnapshot document : appointments.getDocuments()) {
                        Date appointmentDate = document.getDate("timestamp");
                        if (appointmentDate != null) {
                            Calendar appointmentCalendar = Calendar.getInstance();
                            appointmentCalendar.setTime(appointmentDate);
                            unavailableTimes.add(appointmentCalendar);
                        }
                    }

                    // Filter out unavailable time slots
                    for (String timeSlot : timeSlots) {
                        boolean isAvailable = true;
                        for (Calendar appointmentTime : unavailableTimes) {
                            if (timeSlot.equals(formatTime(appointmentTime))) {
                                isAvailable = false;
                                break;
                            }
                        }
                        if (isAvailable) {
                            availableTimeSlots.add(timeSlot);
                        }
                    }

                    // Show available time slots to the user
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Select a Time");
                    builder.setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, availableTimeSlots), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String selectedTime = availableTimeSlots.get(which);
                            int hour = Integer.parseInt(selectedTime.split(":")[0]);
                            int minute = Integer.parseInt(selectedTime.split(":")[1]);
                            selectedDate.set(Calendar.HOUR_OF_DAY, hour);
                            selectedDate.set(Calendar.MINUTE, minute);
                            isTimeSelected = true;
                            Toast.makeText(MainActivity.this, "Selected time: " + selectedTime, Toast.LENGTH_SHORT).show();
                        }
                    });
                    builder.show();
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to fetch appointments: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Confirm button click listener
        confirmButton.setOnClickListener(v -> {
            if (!isDateSelected || !isTimeSelected) {
                Toast.makeText(MainActivity.this, "Please select a valid date and time.", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user == null) {
                Toast.makeText(MainActivity.this, "User not logged in.", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = user.getUid();
            String userEmail = user.getEmail();
            long appointmentTimestamp = selectedDate.getTimeInMillis();
            String formattedDate = String.format("%02d/%02d/%04d %02d:%02d", selectedDate.get(Calendar.DAY_OF_MONTH), selectedDate.get(Calendar.MONTH) + 1, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.HOUR_OF_DAY), selectedDate.get(Calendar.MINUTE));

            // Check if there is already an appointment at the selected date and time
            firestore.collection("appointments")
                    .whereEqualTo("timestamp", appointmentTimestamp)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            // Notify the user if there is already an appointment at the selected date and time
                            Toast.makeText(MainActivity.this, "There is already an appointment at this date and time.", Toast.LENGTH_SHORT).show();
                        } else {
                            // Create a new appointment if there is no existing appointment at the selected date and time
                            Map<String, Object> appointment = new HashMap<>();
                            appointment.put("userId", userId);
                            appointment.put("timestamp", appointmentTimestamp);
                            appointment.put("formattedDate", formattedDate);
                            appointment.put("email", userEmail);

                            firestore.collection("appointments")
                                    .add(appointment)
                                    .addOnSuccessListener(documentReference -> {
                                        Toast.makeText(MainActivity.this, "Appointment successfully created!", Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(MainActivity.this, "Appointment creation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    });
        });

        // Appointments button click listener
        btnAppointment.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MyAppointmentsActivity.class);
            startActivity(intent);
        });

        // Logout button click listener
        btnLogout.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    // Method to fetch appointments for a specific date
    private void fetchAppointmentsForDate(Calendar date, OnFetchAppointmentsListener listener) {
        // Create a time range for the specified date
        Calendar startOfDay = (Calendar) date.clone();
        startOfDay.set(Calendar.HOUR_OF_DAY, 0);
        startOfDay.set(Calendar.MINUTE, 0);
        startOfDay.set(Calendar.SECOND, 0);
        startOfDay.set(Calendar.MILLISECOND, 0);

        Calendar endOfDay = (Calendar) date.clone();
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);

        // Query Firestore for appointments within the specified time range
        firestore.collection("appointments")
                .whereGreaterThanOrEqualTo("timestamp", startOfDay.getTime())
                .whereLessThanOrEqualTo("timestamp", endOfDay.getTime())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null) {
                            listener.onSuccess(querySnapshot);
                        } else {
                            listener.onFailure(new Exception("No appointments found"));
                        }
                    } else {
                        listener.onFailure(task.getException());
                    }
                });
    }

    // Interface for fetching appointments
    interface OnFetchAppointmentsListener {
        void onSuccess(QuerySnapshot appointments);
        void onFailure(Exception e);
    }

    // Method to generate time slots
    private String[] generateTimeSlots() {
        List<String> timeSlots = new ArrayList<>();
        for (int hour = 8; hour <= 18; hour++) {
            timeSlots.add(String.format("%02d:00", hour));
            if (hour < 18) {
                timeSlots.add(String.format("%02d:30", hour));
            }
        }
        return timeSlots.toArray(new String[0]);
    }

    // Method to format time
    private String formatTime(Calendar calendar) {
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        return String.format("%02d:%02d", hour, minute);
    }
}
