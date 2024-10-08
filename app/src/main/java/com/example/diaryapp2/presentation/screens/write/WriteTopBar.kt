package com.example.diaryapp2.presentation.screens.write

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.diaryapp2.model.Diary
import com.example.diaryapp2.model.Mood
import com.example.diaryapp2.presentation.components.DisplayAlertDialog
import com.example.diaryapp2.util.toInstant
import com.maxkeppeker.sheets.core.models.base.rememberSheetState
import com.maxkeppeler.sheets.calendar.CalendarDialog
import com.maxkeppeler.sheets.calendar.models.CalendarConfig
import com.maxkeppeler.sheets.calendar.models.CalendarSelection
import com.maxkeppeler.sheets.clock.ClockDialog
import com.maxkeppeler.sheets.clock.models.ClockSelection
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteTopBar(
    selectedDiary: Diary?,
    moodName: () -> String,
    onDateTimeUpdated: (ZonedDateTime) -> Unit,
    onDeleteClicked: () -> Unit,
    onBackPressed: () -> Unit
) {
    val dateDilog = rememberSheetState()
    val timeDialog = rememberSheetState()

    var currentDate  by remember {
        mutableStateOf(LocalDate.now())
    }
    var currentTime  by remember {
        mutableStateOf(LocalTime.now())
    }
    val formattedDate = remember(key1 = currentDate) {
        DateTimeFormatter.ofPattern("dd MMM yyyy").format(currentDate).uppercase()
    }
    val formattedTime = remember(key1 = currentTime) {
        DateTimeFormatter.ofPattern("hh:mm a").format(currentTime).uppercase()
    }
    var dateTimeUpdated by remember {
        mutableStateOf(false)
    }
    val selectedDiaryDateTime = remember(selectedDiary) {
        if(selectedDiary != null) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(
                Date.from(
                    selectedDiary?.date?.toInstant()
                )
            ).uppercase()
        } else {
            "Unknown"
        }
    }

    LaunchedEffect(key1 = Unit){
        onDateTimeUpdated(
            ZonedDateTime.of(
                currentDate,
                currentTime,
                ZoneId.systemDefault()
            )
        )
    }

    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back Arrow Icon"
                )
            }
        },
        title = {
            Column{
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = moodName(),
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if(selectedDiary != null && dateTimeUpdated) "$formattedDate, $formattedTime"
                    else if(selectedDiary != null) selectedDiaryDateTime
                    else "$formattedDate, $formattedTime",
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    textAlign = TextAlign.Center
                )
            }
        },
        actions = {
            if(dateTimeUpdated){
                IconButton(onClick = {
                    currentDate = LocalDate.now()
                    currentTime = LocalTime.now()
                    dateTimeUpdated = false
                    onDateTimeUpdated(
                        ZonedDateTime.of(
                            currentDate,
                            currentTime,
                            ZoneId.systemDefault()
                        )
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            else {
                IconButton(onClick = { dateDilog.show() }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Date Icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if(selectedDiary != null){
                DeleteDiaryAction(selectedDiary, onDeleteClicked)
            }
        }
    )
    
    CalendarDialog(
        state = dateDilog,
        selection = CalendarSelection.Date{localDate->
            currentDate = localDate
            timeDialog.show()
        },
       config = CalendarConfig(monthSelection = true, yearSelection = true)
    )
    ClockDialog(
        state = timeDialog,
        selection = ClockSelection.HoursMinutes{ hours, minutes ->
            currentTime = LocalTime.of(hours, minutes)
            dateTimeUpdated = true
            onDateTimeUpdated(
                ZonedDateTime.of(
                    currentDate,
                    currentTime,
                    ZoneId.systemDefault()
                )
            )
        }
    )

}

@Composable
fun DeleteDiaryAction(
    selectedDiary: Diary?,
    onDeleteClicked: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var openDialog by remember { mutableStateOf(false) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(text = "Delete")
                   },
            onClick = {
                openDialog = true
                expanded = false
            }
        )
    }
    DisplayAlertDialog(
        title = "Delete",
        message = "Are you sure you want to premanently delete this diary note '${selectedDiary?.title}'",
        dialogOpened = openDialog,
        onYesClicked = onDeleteClicked,
        onCloseDialog = {openDialog = false}
    )
    IconButton(onClick = {expanded = !expanded}) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Overflow Menu Icon",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}