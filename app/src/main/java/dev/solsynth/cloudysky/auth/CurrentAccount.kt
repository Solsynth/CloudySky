package dev.solsynth.cloudysky.auth

data class CurrentAccount(
    val id: String,
    val name: String,
    val nick: String,
    val language: String,
    val bio: String,
    val pictureUrl: String? = null,
) {
    val displayName: String = nick.ifBlank { name }
}
