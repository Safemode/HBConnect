package com.safemode.hbconnect.data.remote.models

/** Body for POST /api/v1/users/login */
data class LoginForm(
    val username: String,
    val password: String,
    val stayLoggedIn: Boolean = true,
)

/** Response from /api/v1/users/login and /api/v1/users/refresh. [token] already carries "Bearer ". */
data class TokenResponse(
    val token: String? = null,
    val expiresAt: String? = null,
    val attachmentToken: String? = null,
)

/** GET /api/v1/status — public application info. */
data class ApiSummary(
    val health: Boolean? = null,
    val versions: List<String>? = null,
    val title: String? = null,
    val message: String? = null,
    val build: Build? = null,
    val allowRegistration: Boolean? = null,
    val demo: Boolean? = null,
)

data class Build(
    val version: String? = null,
    val commit: String? = null,
    val buildTime: String? = null,
)

/** GET /api/v1/users/self wraps the user in { "item": … }. */
data class UserSelfResponse(val item: UserOut? = null)

data class UserOut(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val isSuperuser: Boolean? = null,
    val groupName: String? = null,
)
