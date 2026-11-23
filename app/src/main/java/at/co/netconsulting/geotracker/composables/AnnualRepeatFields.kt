package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun AnnualRepeatFields(
    repeatAnnually: Boolean,
    onRepeatAnnuallyChange: (Boolean) -> Unit,
    repeatYears: String,
    onRepeatYearsChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .toggleable(repeatAnnually, role = Role.Checkbox, onValueChange = onRepeatAnnuallyChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = repeatAnnually,
            onCheckedChange = null,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text("Repeat every year")
    }
    if (repeatAnnually) {
        OutlinedTextField(
            value = repeatYears,
            onValueChange = onRepeatYearsChange,
            label = { Text("Number of years") },
            supportingText = { Text("Includes the first year. The event appears every day in the date range each year.") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
