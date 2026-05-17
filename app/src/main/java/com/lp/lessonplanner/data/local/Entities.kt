package com.lp.lessonplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val actualName: String?,
    val tribe: String?
)

data class SubjectWithCount(
    @Embedded val subject: SubjectEntity,
    val indicatorCount: Int
) {
    @androidx.room.Ignore
    var gradesCounts: Map<String, Int> = emptyMap()
}

@Entity(
    tableName = "curriculum",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["grade"]),
        Index(value = ["subjectId", "grade", "indicatorCode"], unique = true)
    ]
)
data class CurriculumEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val grade: String,
    val strand: String?,
    val subStrand: String?,
    val contentStandard: String?,
    val indicatorCode: String?,
    val indicatorDescription: String?,
    val exemplars: String?,
    val coreCompetencies: String?
)

@Entity(tableName = "saved_plans")
data class SavedPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val week: String,
    val lessonNumber: String? = null,
    val indicatorCode: String?,
    val content: String, // JSON String
    val pdfUri: String? = null,
    val planType: String = "Lesson Plan",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String?
)

@Entity(tableName = "credit_redemptions")
data class CreditRedemptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val amount: Int,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val phone: String,
    val pin: String,
    @androidx.room.ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
