package com.example.githublogin

import android.app.Application
import com.google.firebase.FirebaseApp

class GitHubLoginApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure Firebase is properly initialized
        FirebaseApp.initializeApp(this)
    }
}