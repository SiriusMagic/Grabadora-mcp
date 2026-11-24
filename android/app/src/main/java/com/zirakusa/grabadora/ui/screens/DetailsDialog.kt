package com.zirakusa.grabadora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.grabadora.R
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.ui.theme.RecColors
import java.time.ZoneId

/** «Detalles»: file name, folder, size, date, length (and flags, when it has some). */
@Composable fun DetailsDialog(r: Recording, zone: ZoneId, onClose: () -> Unit) {
    val l = locale()
    Sheet(stringResource(R.string.details), onClose, null) {
        Detail(stringResource(R.string.details_name), r.fileName)
        Detail(stringResource(R.string.details_location), r.location)
        Detail(stringResource(R.string.details_size), Clock.size(r.size, l))
        Detail(stringResource(R.string.details_date), fullDateText(r.date, zone))
        Detail(stringResource(R.string.details_duration), Clock.duration(r.durationMs))
        if (r.markers.isNotEmpty()) Detail(stringResource(R.string.details_markers), r.markers.joinToString("  ") { Clock.position(it) })
    }
}

@Composable private fun Detail(title: String, value: String) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(title, color = RecColors.Muted, fontSize = 13.sp)
    Text(value, fontSize = 16.sp)
}
