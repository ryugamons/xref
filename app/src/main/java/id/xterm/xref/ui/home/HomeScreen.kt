package id.xterm.xref.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import id.xterm.xref.data.repository.ConnectionState
import id.xterm.xref.ui.theme.DarkBackground
import id.xterm.xref.ui.theme.NeonGreen

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "> SYSTEM CONFIGURATION",
            color = NeonGreen,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Login Section
        SectionHeader("LOGIN_MODULE")
        TerminalTextField(
            value = viewModel.refereeId,
            onValueChange = { viewModel.refereeId = it },
            label = "Referee ID"
        )
        Spacer(modifier = Modifier.height(8.dp))
        TerminalTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = "Password",
            isPassword = true
        )
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalButton(
                text = "LOGIN",
                onClick = { viewModel.login() },
                modifier = Modifier.weight(1f),
                enabled = connectionState !is ConnectionState.Connecting && connectionState !is ConnectionState.Connected
            )
            TerminalButton(
                text = "LOGOUT",
                onClick = { viewModel.logout() },
                modifier = Modifier.weight(1f),
                enabled = connectionState is ConnectionState.Connected
            )
        }
        
        Text(
            text = "STATUS: ${connectionState.toString().uppercase()}",
            color = if (connectionState is ConnectionState.Connected) NeonGreen else if (connectionState is ConnectionState.Error) Color.Red else NeonGreen.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Configuration Section
        SectionHeader("ROOM_CONFIG")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalTextField(
                value = viewModel.roomId,
                onValueChange = { viewModel.roomId = it },
                label = "Room Name/ID",
                modifier = Modifier.weight(1f)
            )
            TerminalButton(
                text = "JOIN",
                onClick = { viewModel.joinRoom() },
                modifier = Modifier.padding(top = 8.dp),
                enabled = connectionState is ConnectionState.Connected
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("PARTICIPANTS_LIST")
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            itemsIndexed(viewModel.participants) { index, participant ->
                TerminalTextField(
                    value = participant,
                    onValueChange = { viewModel.updateParticipant(index, it) },
                    label = "Team ${index + 1}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = "[ $title ]",
        color = NeonGreen.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace) },
        modifier = modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(
            color = NeonGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = NeonGreen.copy(alpha = 0.5f),
            focusedLabelColor = NeonGreen,
            unfocusedLabelColor = NeonGreen.copy(alpha = 0.5f),
            cursorColor = NeonGreen,
            focusedTextColor = NeonGreen,
            unfocusedTextColor = NeonGreen
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(4.dp),
        singleLine = true
    )
}

@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = DarkBackground,
            disabledContainerColor = NeonGreen.copy(alpha = 0.2f),
            disabledContentColor = DarkBackground.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
