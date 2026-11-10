package com.example.android.data.model

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val full_name: String,
    val email: String,
    val password: String,
    val year_of_birth: Int,
    val role: String = "patient"
)

data class UserResponse(
    val id: Int,
    val full_name: String,
    val email: String,
    val year_of_birth: Int,
    val role: String = "patient"
)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user: UserResponse
)

data class SessionOut(
    val session_id: String,
    val overall_stability_score: Double?,
    val session_status: String?,
    val risk_label: String?,
    val tapping_stability_match: Double?,
    val tapping_status: String?,
    val drawing_tremor_level: String?,
    val drawing_status: String?,
    val clinical_notes: String? = null,
    val review_status: String? = "Pending Review",
    val reviewed_by: String? = null,
    val created_at: String
)

data class TypingExcerptRequest(
    val excerpts: List<List<List<Long>>>   // excerpts[i] = list of [press_ms, release_ms]
)

// ── Admin & Clinician Portal Models ───────────────────────────────────────
data class AdminDashboardStats(
    val total_patients: Int,
    val total_assessments: Int,
    val flagged_assessments: Int,
    val pending_reviews: Int
)

data class PatientSummary(
    val id: Int,
    val full_name: String,
    val email: String,
    val year_of_birth: Int,
    val role: String,
    val total_assessments: Int,
    val latest_score: Double?,
    val latest_risk_label: String?,
    val latest_screening_date: String?
)

data class FlaggedSession(
    val session_id: String,
    val patient_id: Int,
    val patient_name: String,
    val patient_email: String,
    val patient_birth_year: Int?,
    val overall_stability_score: Double?,
    val risk_label: String?,
    val drawing_tremor_level: String?,
    val drawing_status: String?,
    val tapping_status: String?,
    val review_status: String,
    val clinical_notes: String?,
    val reviewed_by: String?,
    val created_at: String
)

data class ClinicalReviewRequest(
    val clinical_notes: String,
    val review_status: String,
    val override_risk_label: String? = null
)

data class DetailedScreeningReview(
    val sessionId: String = "",
    val date: String = "",
    val overallScore: Int = 88,
    val riskLabel: String = "Stable",
    val spiralTremorLevel: String = "Low Tremor",
    val spiralStatus: String = "Fluid",
    val waveTremorLevel: String = "Low Tremor",
    val waveStatus: String = "Fluid",
    val tappingStabilityMatch: Float = 92.0f,
    val tappingStatus: String = "Steady",
    val clinicalNotes: String? = null,
    val reviewedBy: String? = null
)

object SessionState {
    var currentSessionId: String = java.util.UUID.randomUUID().toString()
    var selectedSessionForReview: DetailedScreeningReview? = null

    fun startNewSession(): String {
        currentSessionId = java.util.UUID.randomUUID().toString()
        selectedSessionForReview = null
        return currentSessionId
    }
}
