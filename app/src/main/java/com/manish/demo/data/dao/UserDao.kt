package com.manish.demo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.manish.demo.entities.UserEntity

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("UPDATE users SET otpCode = :otp WHERE email = :email")
    suspend fun updateOtp(email: String, otp: String)

    @Query("SELECT * FROM users WHERE email = :email AND otpCode = :otp LIMIT 1")
    suspend fun verifyOtp(email: String, otp: String): UserEntity?

    @Query("UPDATE users SET passwordHash = :password, isVerified = 1 WHERE email = :email")
    suspend fun setPassword(email: String, password: String)
}
