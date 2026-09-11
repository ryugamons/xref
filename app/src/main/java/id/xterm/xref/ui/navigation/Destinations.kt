package id.xterm.xref.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination
    @Serializable
    data object Bracket : Destination
    @Serializable
    data object Statistics : Destination
    @Serializable
    data object Room : Destination
    @Serializable
    data object Dashboard : Destination
}
