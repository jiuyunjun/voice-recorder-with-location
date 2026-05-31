package com.example.voicerecorderlocation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicerecorderlocation.ui.theme.Hair
import com.example.voicerecorderlocation.ui.theme.NumFamily
import com.example.voicerecorderlocation.ui.theme.Panel
import com.example.voicerecorderlocation.ui.theme.TextDim
import com.example.voicerecorderlocation.ui.theme.TextHi
import com.example.voicerecorderlocation.ui.theme.TextMut

@Composable
fun StatChip(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = TextHi,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(12.dp))
            .border(1.dp, Hair, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(label.uppercase(), color = TextDim, fontSize = 10.sp, letterSpacing = 0.4.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontFamily = NumFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            if (unit != null) Text(
                " $unit", color = TextMut, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}
