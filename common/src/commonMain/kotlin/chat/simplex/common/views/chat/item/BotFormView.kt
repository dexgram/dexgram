package chat.simplex.common.views.chat.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.bot.BotForm
import chat.simplex.common.views.bot.FormFieldType

private val FormBg1 = Color(0xFF1A1F36)
private val FormBg2 = Color(0xFF0D1025)
private val FieldBg = Color(0xFF2A3352)
private val LabelColor = Color(0xFFCBD5E1)
private val TitleColor = Color(0xFFF1F5F9)
private val SubmitColor = Color(0xFF059669)

@Composable
fun BotFormView(
    form: BotForm,
    text: String? = null,
    onSubmit: (Map<String, String>) -> Unit
) {
    val fieldValues = remember { mutableStateMapOf<String, String>() }
    val expandedDropdown = remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(FormBg1, FormBg2),
                    start = Offset(0f, 0f),
                    end = Offset(400f, 400f)
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = form.title,
            color = TitleColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (text != null) {
            Text(text = text, color = LabelColor, fontSize = 13.sp)
        }

        for (field in form.fields) {
            Column {
                Text(
                    text = if (field.required) "${field.label} *" else field.label,
                    color = LabelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                when (field.fieldType) {
                    FormFieldType.DROPDOWN -> {
                        val options = field.options ?: emptyList()
                        val current = fieldValues[field.name] ?: ""
                        val isExpanded = expandedDropdown.value == field.name

                        Box {
                            OutlinedButton(
                                onClick = { expandedDropdown.value = if (isExpanded) null else field.name },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(backgroundColor = FieldBg)
                            ) {
                                Text(
                                    text = current.ifEmpty { field.placeholder ?: "Select..." },
                                    color = if (current.isEmpty()) LabelColor.copy(alpha = 0.5f) else TitleColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            DropdownMenu(
                                expanded = isExpanded,
                                onDismissRequest = { expandedDropdown.value = null }
                            ) {
                                options.forEach { option ->
                                    DropdownMenuItem(onClick = {
                                        fieldValues[field.name] = option
                                        expandedDropdown.value = null
                                    }) {
                                        Text(option)
                                    }
                                }
                            }
                        }
                    }
                    FormFieldType.TEXTAREA -> {
                        OutlinedTextField(
                            value = fieldValues[field.name] ?: "",
                            onValueChange = { fieldValues[field.name] = it },
                            modifier = Modifier.fillMaxWidth().height(90.dp),
                            placeholder = { field.placeholder?.let { Text(it, color = LabelColor.copy(alpha = 0.5f)) } },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = TitleColor,
                                backgroundColor = FieldBg,
                                focusedBorderColor = SubmitColor,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = SubmitColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 4
                        )
                    }
                    else -> {
                        val keyboardType = when (field.fieldType) {
                            FormFieldType.NUMBER -> KeyboardType.Number
                            FormFieldType.EMAIL -> KeyboardType.Email
                            FormFieldType.PHONE -> KeyboardType.Phone
                            else -> KeyboardType.Text
                        }
                        OutlinedTextField(
                            value = fieldValues[field.name] ?: "",
                            onValueChange = { fieldValues[field.name] = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            placeholder = { field.placeholder?.let { Text(it, color = LabelColor.copy(alpha = 0.5f)) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = TitleColor,
                                backgroundColor = FieldBg,
                                focusedBorderColor = SubmitColor,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = SubmitColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onSubmit(fieldValues.toMap()) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = SubmitColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = form.submitLabel,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
