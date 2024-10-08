package com.example.diaryapp2.presentation.screens.write

import Constants.WRITE_SCREEN_ARGUMENT_KEY
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diaryapp2.data.database.ImageToDeleteDAO
import com.example.diaryapp2.data.database.ImageToUploadDAO
import com.example.diaryapp2.data.database.entity.ImageToDelete
import com.example.diaryapp2.data.database.entity.ImageToUpload
import com.example.diaryapp2.data.repository.MongoDB
import com.example.diaryapp2.model.*
import com.example.diaryapp2.util.fetchImagesFirebase
import com.example.diaryapp2.util.toRealmInstant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageToUploadDao: ImageToUploadDAO,
    private val imageToDeleteDao: ImageToDeleteDAO
): ViewModel() {
    val galleryState = GalleryState()
    var uiState by mutableStateOf(UiState())
    private set

    init {
        getDiaryIdArgument()
        fetchSelectedDiary()
    }

    private fun getDiaryIdArgument() {
        uiState = uiState.copy(
            selectedDiaryId = savedStateHandle.get<String>(
                key = WRITE_SCREEN_ARGUMENT_KEY
            )
        )
    }

    // without flow
//    private fun fetchSelectedDiary() {
//        if(uiState.selectedDiaryId != null){
//            viewModelScope.launch ( Dispatchers.Main ){
//                val diary = MongoDB.getSelectedDiary(
//                    diaryId = ObjectId.Companion.from(uiState.selectedDiaryId!!)
//                )
//                if(diary is RequestState.Success){
//                    setSelectedDiary(diary = diary.data)
//                    setTitle(title = diary.data.title)
//                    setDescription(description = diary.data.description)
//                    setMood(mood = Mood.valueOf(diary.data.mood))
//                }
//            }
//        }
//    }

    private fun fetchSelectedDiary() {
        if(uiState.selectedDiaryId != null){
            viewModelScope.launch ( Dispatchers.Main ){
                MongoDB.getSelectedDiary(
                    diaryId = ObjectId.Companion.from(uiState.selectedDiaryId!!)
                ).catch {
                    emit(RequestState.Error(Exception("Diary is already deleted.")))
                }.collect{diary ->
                    if(diary is RequestState.Success){
                        setSelectedDiary(diary = diary.data)
                        setTitle(title = diary.data.title)
                        setDescription(description = diary.data.description)
                        setMood(mood = Mood.valueOf(diary.data.mood))

                        fetchImagesFirebase(
                            remoteImagePaths = diary.data.images,
                            onImageDownload = { downloadImage ->
                                galleryState.addImage(
                                    GalleryImage(
                                        image = downloadImage,
                                        remoteImagePath = extractImagePath(
                                            fullImageUrl = downloadImage.toString()
                                        ),
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setSelectedDiary(diary: Diary) {
        uiState = uiState.copy(selectedDiary = diary)
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(title = title)
    }

    fun setDescription(description: String){
        uiState = uiState.copy(description = description)
    }

    private fun setMood(mood: Mood){
        uiState = uiState.copy(mood = mood)
    }

    fun updateDateTime(zonedDateTime: ZonedDateTime){
        uiState = uiState.copy(updatedDateTime = zonedDateTime.toInstant().toRealmInstant())
    }

    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if(uiState.selectedDiaryId != null){
                updateDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            } else {
                insertDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            }
        }
    }

    private suspend fun insertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ){
        Log.d("insert", "diary insertion")
        val result = MongoDB.insertDiary(diary = diary.apply{
            date = uiState.updatedDateTime!!
        })
        if(result is RequestState.Success){
            uploadImagesToFirebase()
            withContext(Dispatchers.Main){
                onSuccess()
            }
        } else if (result is RequestState.Error){
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    private suspend fun updateDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.updateDiary(diary = diary.apply {
            _id = ObjectId.Companion.from(uiState.selectedDiaryId!!)
            date = if(uiState.updatedDateTime != null){
                uiState.updatedDateTime!!
            } else {
                uiState.selectedDiary!!.date
            }
        })
        if(result is RequestState.Success){
            uploadImagesToFirebase()
            deleteImagesFromFirebase()
            withContext(Dispatchers.Main){
                onSuccess()
            }
        } else if (result is RequestState.Error){
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    fun deleteDiary(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ){
        viewModelScope.launch(Dispatchers.IO) {
            if(uiState.selectedDiaryId != null){
                val result = MongoDB.deleteDiary(id = ObjectId.from(uiState.selectedDiaryId!!))
                if(result is RequestState.Success){
                    withContext(Dispatchers.Main){
                        uiState.selectedDiary?.images?.let { deleteImagesFromFirebase(images = it) }
                        onSuccess()
                    }
                } else if(result is RequestState.Error){
                    withContext(Dispatchers.Main){
                        onError(result.error.message.toString())
                    }
                }
            }
        }
    }

    fun addImage(image: Uri, imageType: String){
        val remoteImagePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}" +
                "/${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"
        Log.d("WriteViewModel", "$remoteImagePath")
        galleryState.addImage(
            GalleryImage(
                image = image,
                remoteImagePath = remoteImagePath
            )
        )
    }

    private fun uploadImagesToFirebase(){
        val storage = FirebaseStorage.getInstance().reference
        galleryState.images.forEach{galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)
            imagePath.putFile(galleryImage.image).addOnProgressListener {
                val sessionUri = it.uploadSessionUri
                if(sessionUri != null){
                    viewModelScope.launch(Dispatchers.IO) {
                        imageToUploadDao.addImageToUpload(
                            ImageToUpload(
                                remoteImagePath = galleryImage.remoteImagePath,
                                imageUri = galleryImage.image.toString(),
                                sessionUri = sessionUri.toString()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null){
        val storage = FirebaseStorage.getInstance().reference
        if(images != null){
            images.forEach{ remotePath ->
                storage.child(remotePath).delete()
                    .addOnFailureListener{
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(remoteImagePath = remotePath)
                            )
                        }
                    }
            }
        } else {
            galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach{remoteImage ->
                storage.child(remoteImage).delete()
                    .addOnFailureListener{
                        viewModelScope.launch(Dispatchers.IO) {
                            imageToDeleteDao.addImageToDelete(
                                ImageToDelete(remoteImagePath = remoteImage)
                            )
                        }
                    }
            }
        }
    }

    private fun extractImagePath(fullImageUrl: String): String{
        val chunks = fullImageUrl.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${Firebase.auth.currentUser?.uid}/$imageName"
    }
}

data class UiState(
    val selectedDiaryId: String? = null,
    val selectedDiary: Diary? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val updatedDateTime: RealmInstant? = null
)