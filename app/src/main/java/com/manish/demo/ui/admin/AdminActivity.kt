package com.manish.demo.ui.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manish.demo.viewmodel.AdminViewModel


class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // This is where we call the full code I gave you earlier
            val viewModel: AdminViewModel = viewModel()
            MoviesManagementScreen(viewModel = viewModel)
        }
    }
}