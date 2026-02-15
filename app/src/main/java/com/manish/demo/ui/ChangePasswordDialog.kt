package com.manish.demo.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: (String) -> Unit // Now returns a message string
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Visibility states for each password field
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        title = { Text("Change Password", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Old Password Field with eye icon
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Old Password") },
                    visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2ECC71),
                        cursorColor = Color(0xFF2ECC71)
                    ),
                    trailingIcon = {
                        IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                            Icon(
                                imageVector = if (oldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (oldPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    }
                )

                // New Password Field with eye icon
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2ECC71),
                        cursorColor = Color(0xFF2ECC71)
                    ),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (newPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    }
                )

                // Confirm Password Field with eye icon
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2ECC71),
                        cursorColor = Color(0xFF2ECC71)
                    ),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = Color(0xFF2ECC71)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71)),
                onClick = {

                    // Validation
                    if (oldPassword.isEmpty() || newPassword.isEmpty()) {
                        onPasswordChanged("Please fill all fields")
                        return@Button
                    }

                    if (newPassword != confirmPassword) {
                        onPasswordChanged("New passwords do not match!")
                        return@Button
                    }

                    if (newPassword.length < 8) {
                        onPasswordChanged("Password must be at least 8 characters")
                        return@Button
                    }

                    isLoading = true
                    val user = FirebaseAuth.getInstance().currentUser
                    val credential =
                        EmailAuthProvider.getCredential(user?.email!!, oldPassword)

                    // Re-authenticate
                    user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->

                        if (reAuthTask.isSuccessful) {

                            user.updatePassword(newPassword)
                                .addOnCompleteListener { updateTask ->

                                    isLoading = false

                                    if (updateTask.isSuccessful) {

                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(user.uid)
                                            .update(
                                                "passwordLastUpdated",
                                                com.google.firebase.Timestamp.now()
                                            )

                                        onPasswordChanged("Password changed successfully")

                                    } else {
                                        onPasswordChanged(
                                            updateTask.exception?.message
                                                ?: "Password update failed"
                                        )
                                    }
                                }

                        } else {
                            isLoading = false
                            onPasswordChanged("Incorrect old password")
                        }
                    }
                }
            ) {
                Text("UPDATE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(
                    "CANCEL",
                    color = Color.Gray
                )
            }
        }
    )
}