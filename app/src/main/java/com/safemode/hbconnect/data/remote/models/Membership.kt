package com.safemode.hbconnect.data.remote.models

/** A member of a group (GET /api/v1/groups/members). The API exposes no role field. */
data class GroupMember(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
)

/** A pending invitation when listing (GET /api/v1/groups/invitations); carries no token. */
data class GroupInvitation(
    val id: String? = null,
    val expiresAt: String? = null,
    val uses: Int = 0,
    val group: Group? = null,
)

/** Body for POST /api/v1/groups/invitations. [expiresAt] omitted → server defaults to 24h. */
data class GroupInvitationCreate(
    val uses: Int,
    val expiresAt: String? = null,
)

/** Response from creating an invitation; [token] is only returned here, never when listing. */
data class GroupInvitationCreated(
    val id: String? = null,
    val token: String? = null,
    val expiresAt: String? = null,
    val uses: Int = 0,
)

/** Response from accepting an invitation (POST /groups/invitations/{token}): the joined group. */
data class GroupAcceptInvitation(
    val id: String? = null,
    val name: String? = null,
)
