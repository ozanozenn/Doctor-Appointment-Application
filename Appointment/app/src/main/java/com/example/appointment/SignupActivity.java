package com.example.appointment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    // Firebase Authentication instance
    private FirebaseAuth firebaseAuth;

    // UI components
    private EditText signupEmail;
    private EditText signupPassword;
    private EditText signupUsername;
    private Button signupButton;
    private TextView loginRedirectText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Get Firebase Auth instance
        firebaseAuth = FirebaseAuth.getInstance();

        // Find UI elements
        signupEmail = findViewById(R.id.signup_email);
        signupPassword = findViewById(R.id.signup_password);
        signupUsername = findViewById(R.id.signup_username);
        signupButton = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);

        // Set up signup button click listener
        signupButton.setOnClickListener(v -> {
            // Get email, password, and username from user input
            String email = signupEmail.getText().toString().trim();
            String password = signupPassword.getText().toString().trim();
            String username = signupUsername.getText().toString().trim();

            // Ensure all fields are filled
            if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
                Toast.makeText(SignupActivity.this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create user with Firebase
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Signup successful, get user information
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            if (user != null) {
                                Log.d("Signup", "createUserWithEmail:success");
                                Toast.makeText(SignupActivity.this, "Signup successful", Toast.LENGTH_SHORT).show();

                                // Save user to Firestore
                                saveUserToDatabase(user, username);

                                // Send email verification (optional)
                                sendEmailVerification(user);

                                // Redirect to MainActivity
                                updateUI(user);
                            } else {
                                Log.w("Signup", "createUserWithEmail: user is null");
                                Toast.makeText(SignupActivity.this, "Signup failed, user could not be created.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // Signup failed, show error message
                            Log.w("Signup", "createUserWithEmail:failure", task.getException());
                            Toast.makeText(SignupActivity.this, "Signup failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    });
        });

        // Redirect to login page
        loginRedirectText.setOnClickListener(v -> {
            // Create an intent to navigate to the login page
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }

    // Method to save user to Firestore
    private void saveUserToDatabase(FirebaseUser user, String username) {
        // Get Firestore instance
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Create a map of user data
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUid());
        userData.put("email", user.getEmail());
        userData.put("username", username);

        // Save user data to Firestore
        db.collection("users")
                .document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d("Signup", "User successfully saved to Firestore"))
                .addOnFailureListener(e -> Log.w("Signup", "Error saving user to Firestore", e));
    }

    // Method to send email verification
    private void sendEmailVerification(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d("Signup", "Verification email sent to " + user.getEmail());
                        Toast.makeText(SignupActivity.this, "Verification email sent. Please check your email.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("Signup", "sendEmailVerification", task.getException());
                        Toast.makeText(SignupActivity.this, "Failed to send verification email.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Method to update the UI based on user login status
    private void updateUI(FirebaseUser user) {
        if (user != null) {
            // User is signed in, navigate to MainActivity
            Intent intent = new Intent(SignupActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
}
