package com.smartfeed.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.smartfeed.data.local.entity.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CategoryEntity> categories);

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    List<CategoryEntity> getAll();

    @Query("UPDATE categories SET is_subscribed = :subscribed WHERE id = :categoryId")
    void updateSubscribed(long categoryId, boolean subscribed);
}
