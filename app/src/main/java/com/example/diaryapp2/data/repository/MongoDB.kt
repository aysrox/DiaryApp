package com.example.diaryapp2.data.repository

import Constants.APP_ID
import android.util.Log
import com.example.diaryapp2.model.Diary
import com.example.diaryapp2.model.RequestState
import com.example.diaryapp2.util.toInstant
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.time.ZonedDateTime


object MongoDB: MongoRepository {
    private val app = App.create(APP_ID)
    private val user = app.currentUser
    private lateinit var realm: Realm

    init {
        Log.d("diary home mongoDB configure", user.toString())
        configureTheRealm()
    }

    override fun configureTheRealm() {
        if(user != null){
            val config = SyncConfiguration.Builder(user, setOf(Diary::class))
                .initialSubscriptions(rerunOnOpen = true){sub ->
                    add(
                        query = sub.query<Diary>("ownerId == $0", user.identity),
                        name = "User's Diaries"
                    )
                }
                .log(LogLevel.ALL)
                .build()
            realm = Realm.open(config)
            Log.d("diary home mongoDB realm", realm.toString())
        }
    }

    override fun getAllDiaries(): Flow<Diaries> {
        return if(user != null){
            try{
                Log.d("diary home mongoDB user1", user.identity)
                realm.query<Diary>(query = "ownerId == $0", user.identity)
                    .sort(property = "date", Sort.DESCENDING)
                    .asFlow()
                    .map{ result ->
                        RequestState.Success(
                            data = result.list.groupBy {
                                it.date.toInstant()
                                    .atZone((ZoneId.systemDefault()))
                                    .toLocalDate()
                            }
                        )
                    }
            } catch (e : Exception){
                flow { emit(RequestState.Error(e))}
            }
        } else {
            flow {
                emit(RequestState.Error(UserNotAuthenticatedException()))
            }
        }
    }

    override fun getFilteredDiaries(zonedDateTime: ZonedDateTime): Flow<Diaries> {
        return if(user != null){
            try{
                Log.d("diary home mongoDB user1", user.identity)
                realm.query<Diary>(
                    query = "ownerId == $0 AND date < $1 AND date > $2",
                    user.identity,
                    RealmInstant.from(zonedDateTime.plusDays(1).toInstant().epochSecond, 0),
                    RealmInstant.from(zonedDateTime.minusDays(1).toInstant().epochSecond, 0)
                )
                    .asFlow()
                    .map{ result ->
                        RequestState.Success(
                            data = result.list.groupBy {
                                it.date.toInstant()
                                    .atZone((ZoneId.systemDefault()))
                                    .toLocalDate()
                            }
                        )
                    }
            } catch (e : Exception){
                flow { emit(RequestState.Error(e))}
            }
        } else {
            flow {
                emit(RequestState.Error(UserNotAuthenticatedException()))
            }
        }
    }

      // without flow
//    override fun getSelectedDiary(diaryId: ObjectId): RequestState<Diary> {
//        return if(user != null){
//            try{
//                val diary = realm.query<Diary>(query = "_id == $0", diaryId).find().first()
//                RequestState.Success(data = diary)
//            } catch(e: Exception){
//                RequestState.Error(e)
//            }
//        } else {
//            RequestState.Error(UserNotAuthenticatedException())
//        }
//    }

    override fun getSelectedDiary(diaryId: ObjectId): Flow<RequestState<Diary>> {
        return if(user != null){
            try{
                realm.query<Diary>(query = "_id == $0", diaryId).asFlow().map {
                    RequestState.Success(data = it.list.first())
                }
            } catch(e: Exception){
                flow {emit(RequestState.Error(e))}
            }
        } else {
            flow {emit(RequestState.Error(UserNotAuthenticatedException()))}
        }
    }

    override suspend fun insertDiary(diary: Diary): RequestState<Diary> {
        return if(user != null){
            realm.write {
                try{
                    val addedDiary = copyToRealm(diary.apply {
                        ownerId = user.identity
                    })
                    RequestState.Success(diary)
                } catch(e: Exception){
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun updateDiary(diary: Diary): RequestState<Diary> {
        return if(user != null){
            realm.write {
                val queriedDiary = query<Diary>(query = "_id == $0", diary._id).first().find()
                if(queriedDiary != null){
                    queriedDiary.title = diary.title
                    queriedDiary.description = diary.description
                    queriedDiary.mood = diary.mood
                    queriedDiary.images = diary.images
                    queriedDiary.date = diary.date
                    RequestState.Success(data = queriedDiary)
                } else {
                    RequestState.Error(error = Exception("Queried Diary does not exist."))
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun deleteDiary(id: ObjectId): RequestState<Diary> {
        return if(user != null){
            realm.write {
                try{
                    val diary = query<Diary>(query = "_id == $0 AND ownerId == $1", id, user.identity).find().first()
                    delete(diary)
                    RequestState.Success(data = diary)
                } catch (e: Exception){
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }

    override suspend fun deleteAllDiaries(): RequestState<Boolean> {
        return if(user != null){
            realm.write {
                val diaries = query<Diary>(query ="ownerId == $0", user.identity).find()
                try{
                    delete(diaries)
                    RequestState.Success(data = true)
                } catch (e:Exception){
                    RequestState.Error(e)
                }
            }
        } else {
            RequestState.Error(UserNotAuthenticatedException())
        }
    }
}

private class UserNotAuthenticatedException : Exception("User is not Logged in.")