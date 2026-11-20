package com.stock.trainer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        KLineEntity::class,
        TradeRecordEntity::class,
        TrainingSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KLineDatabase : RoomDatabase() {
    abstract fun kLineDao(): KLineDao
    abstract fun tradeRecordDao(): TradeRecordDao
    abstract fun trainingSessionDao(): TrainingSessionDao
}