package com.stock.trainer.di

import android.content.Context
import androidx.room.Room
import com.stock.trainer.data.local.KLineDao
import com.stock.trainer.data.local.KLineDatabase
import com.stock.trainer.data.local.TradeRecordDao
import com.stock.trainer.data.local.TrainingSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KLineDatabase {
        return Room.databaseBuilder(
            context,
            KLineDatabase::class.java,
            "stock_training.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideKLineDao(database: KLineDatabase): KLineDao = database.kLineDao()

    @Provides
    fun provideTradeRecordDao(database: KLineDatabase): TradeRecordDao = database.tradeRecordDao()

    @Provides
    fun provideTrainingSessionDao(database: KLineDatabase): TrainingSessionDao = database.trainingSessionDao()
}