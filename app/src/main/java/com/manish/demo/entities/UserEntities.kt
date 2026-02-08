package com.manish.demo.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

// 1. MAIN USER TABLE (Auth)
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val userId: Long = 0,
    val email: String,
    val passwordHash: String?, // Nullable initially (before password setup)
    val role: String = "USER", // "USER" or "ADMIN"
    val isVerified: Boolean = false, // False until OTP is correct
    val otpCode: String? = null, // Temporary storage for OTP
    val createdAt: Long = System.currentTimeMillis()
)

// 2. USER PROFILE (Details) - One-to-One with User
@Entity(
    tableName = "user_profiles",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE // If User deleted, delete profile
        )
    ]
)
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val profileId: Long = 0,
    @ColumnInfo(index = true) val userId: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val profilePicUrl: String?
)

// 3. SUBSCRIPTION PLANS (The Menu) - e.g., "Premium", "Basic"
@Entity(tableName = "subscription_plans")
data class SubscriptionPlanEntity(
    @PrimaryKey(autoGenerate = true) val planId: Int = 0,
    val planName: String,     // "Gold Plan"
    val price: Double,        // 9.99
    val durationDays: Int,    // 30
    val maxScreens: Int,      // 4
    val resolution: String    // "4K HDR"
)

// 4. USER SUBSCRIPTIONS (Who bought what?)
@Entity(
    tableName = "user_subscriptions",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["userId"], childColumns = ["userId"]),
        ForeignKey(entity = SubscriptionPlanEntity::class, parentColumns = ["planId"], childColumns = ["planId"])
    ]
)
data class UserSubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val subId: Long = 0,
    @ColumnInfo(index = true) val userId: Long,
    @ColumnInfo(index = true) val planId: Int,
    val startDate: Long,
    val endDate: Long,
    val status: String // "ACTIVE", "EXPIRED", "CANCELLED"
)