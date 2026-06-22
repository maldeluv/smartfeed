package com.smartfeed.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.smartfeed.data.local.dao.ArticleDao;
import com.smartfeed.data.local.dao.CategoryDao;
import com.smartfeed.data.local.dao.PendingEventDao;
import com.smartfeed.data.local.dao.SavedArticleDao;
import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.CategoryEntity;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.local.entity.SavedArticleEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                ArticleEntity.class,
                CategoryEntity.class,
                SavedArticleEntity.class,
                PendingEventEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "smartfeed.db";
    private static volatile AppDatabase instance;

    public static final ExecutorService DATABASE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).build();
                }
            }
        }
        return instance;
    }

    public abstract ArticleDao articleDao();

    public abstract CategoryDao categoryDao();

    public abstract SavedArticleDao savedArticleDao();

    public abstract PendingEventDao pendingEventDao();
}
