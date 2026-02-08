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
fun ChangePasswordDialog(onDismiss: () -> Unit, onPasswordChanged: () -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPasswordVisibility by remember { mutableStateOf(false) }
    var newPasswordVisibility by remember { mutableStateOf(false) }
    var confirmPasswordVisibility by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val context = LocalContext.current

    fun triggerShake() {
        coroutineScope.launch {
            shake.animateTo(10f, tween(50))
            shake.animateTo(-10f, tween(50))
            shake.animateTo(10f, tween(50))
            shake.animateTo(0f, tween(50))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.graphicsLayer(translationX = shake.value),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2c2c2c))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Change Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                }

                val customColors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.White,
                    unfocusedIndicatorColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Old Password") },
                    visualTransformation = if(oldPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { oldPasswordVisibility = !oldPasswordVisibility }) {
                            Icon(if(oldPasswordVisibility) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = if(newPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisibility = !newPasswordVisibility }) {
                            Icon(if(newPasswordVisibility) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = if(confirmPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisibility = !confirmPasswordVisibility }) {
                            Icon(if(confirmPasswordVisibility) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = customColors
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        errorMessage = null
                        if(newPassword != confirmPassword) {
                            errorMessage = "Passwords do not match."
                            triggerShake()
                            return@Button
                        }

                        isLoading = true
                        val user = FirebaseAuth.getInstance().currentUser
                        if (user == null || user.email == null) {
                            Toast.makeText(context, "Could not change password for this account type.", Toast.LENGTH_LONG).show()
                            isLoading = false
                            return@Button
                        }
                        
                        val credential = EmailAuthProvider.getCredential(user.email!!, oldPassword)

                        user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                            if (reauthTask.isSuccessful) {
                                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        val db = FirebaseFirestore.getInstance()
                                        db.collection("users").document(user.uid).update("passwordLastUpdated", FieldValue.serverTimestamp())
                                        isLoading = false
                                        onPasswordChanged()
                                    } else {
                                        isLoading = false
                                        errorMessage = updateTask.exception?.message
                                        triggerShake()
                                    }
                                }
                            } else {
                                isLoading = false
                                errorMessage = reauthTask.exception?.message
                                triggerShake()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}