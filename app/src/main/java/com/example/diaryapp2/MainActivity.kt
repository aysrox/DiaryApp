package com.example.diaryapp2

import Constants.APP_ID
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.diaryapp2.data.database.ImageToDeleteDAO
import com.example.diaryapp2.data.database.ImageToUploadDAO
import com.example.diaryapp2.data.repository.MongoDB
import com.example.diaryapp2.navigation.Screen
import com.example.diaryapp2.navigation.SetupNavGraph
import com.example.diaryapp2.ui.theme.DiaryApp2Theme
import com.example.diaryapp2.util.retryDeletingImageToFirebase
import com.example.diaryapp2.util.retryUploadingImageToFirebase
import com.google.firebase.FirebaseApp
import dagger.hilt.android.AndroidEntryPoint
import io.realm.kotlin.mongodb.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var imageToUploadDAO: ImageToUploadDAO

    @Inject
    lateinit var imageToDeleteDAO: ImageToDeleteDAO

    var keepSplashOpened = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().setKeepOnScreenCondition{
            keepSplashOpened
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        FirebaseApp.initializeApp(this)
        setContent {
            DiaryApp2Theme {
                val navController = rememberNavController()
                SetupNavGraph(
                    startDestination = getStartDestination(),
                    navController = navController,
                    onDataLoaded = {
                        keepSplashOpened = false
                    }
                )
            }
        }

        cleanupCheck(scope = lifecycleScope, imageToUploadDAO = imageToUploadDAO, imageToDeleteDAO = imageToDeleteDAO)
    }
}

private fun cleanupCheck(
    scope: CoroutineScope,
    imageToUploadDAO: ImageToUploadDAO,
    imageToDeleteDAO: ImageToDeleteDAO
){
    scope.launch(Dispatchers.IO){
        val result = imageToUploadDAO.getAllImages()
        result.forEach{imageToUpload ->
            retryUploadingImageToFirebase(
                imageToUpload,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToUploadDAO.cleanupImage(imageId = imageToUpload.id)
                    }
                }
            )
        }

        val result2 = imageToDeleteDAO.getAllImages()
        result2.forEach{imageToDelete ->
            retryDeletingImageToFirebase(
                imageToDelete,
                onSuccess = {
                    scope.launch(Dispatchers.IO) {
                        imageToUploadDAO.cleanupImage(imageId = imageToDelete.id)
                    }
                }
            )
        }
    }
}

private fun getStartDestination(): String {
    val user = App.create(APP_ID).currentUser
    return if (user != null && user.loggedIn) Screen.Home.route
    else Screen.Authentication.route
}