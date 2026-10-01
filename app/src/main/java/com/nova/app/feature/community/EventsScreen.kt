package com.nova.app.feature.community

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.ui.NovaCard
import com.nova.app.ui.theme.PurpleMain

@Composable
fun EventsScreen() {
    val events = listOf(
        EventData("Coffee & Connections", "Tomorrow, 10:00 AM", "Starbucks Reserve"),
        EventData("Art Gallery Walk", "Sat, 2:00 PM", "Modern Art Museum"),
        EventData("Sunset Hiking", "Sun, 5:00 PM", "Griffith Park")
    )
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(stringResource(R.string.events_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(events) { event ->
                    EventItem(event)
                }
            }
        }
    }
}

@Composable
fun EventItem(event: EventData) {
    NovaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(event.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(event.time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(event.location, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = NovaBrand.Start),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.events_join))
            }
        }
    }
}

data class EventData(val title: String, val time: String, val location: String)
