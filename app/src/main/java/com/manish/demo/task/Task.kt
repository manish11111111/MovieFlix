package com.manish.demo.task

import androidx.room.Entity
import androidx.room.*

@Entity
data class Task(
    @PrimaryKey val num: Int=0,
)