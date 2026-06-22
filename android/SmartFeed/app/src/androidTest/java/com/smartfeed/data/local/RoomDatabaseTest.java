package com.smartfeed.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.CategoryEntity;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.local.entity.SavedArticleEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class RoomDatabaseTest {

    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void daoStoresFeedSavedCategoryAndPendingEvent() {
        CategoryEntity category = new CategoryEntity(
                12L,
                "Architecture",
                "architecture",
                "System design",
                true,
                1000L
        );
        ArticleEntity article = new ArticleEntity(
                120L,
                "Room article",
                "Summary",
                null,
                null,
                12L,
                "Architecture",
                "SmartFeed",
                "2026-06-20T12:00:00Z",
                "2026-06-20T12:00:00Z",
                42.5,
                true,
                true,
                true,
                1000L
        );
        PendingEventEntity pendingEvent = new PendingEventEntity(
                "event-1",
                "session-1",
                "view_article",
                120L,
                12L,
                "2026-06-20T12:00:00Z",
                "{}",
                "{}",
                0,
                null,
                1000L
        );

        database.categoryDao().upsertAll(Collections.singletonList(category));
        database.articleDao().upsert(article);
        database.savedArticleDao().upsert(new SavedArticleEntity(120L, 1000L));
        database.pendingEventDao().upsert(pendingEvent);

        assertEquals(1, database.categoryDao().getAll().size());
        assertEquals(1, database.articleDao().getFeed().size());
        assertEquals(1, database.savedArticleDao().getSavedArticles().size());
        assertEquals(1, database.pendingEventDao().count());
        assertTrue(database.savedArticleDao().getSavedArticles().get(0).isSaved());

        database.savedArticleDao().delete(120L);
        assertTrue(database.savedArticleDao().getSavedArticles().isEmpty());
    }
}
