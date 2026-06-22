package com.smartfeed.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import com.smartfeed.data.local.entity.PendingEventEntity;

import java.util.List;

@Dao
public interface PendingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingEventEntity event);

    @Query("SELECT * FROM pending_events ORDER BY created_at ASC LIMIT :limit")
    List<PendingEventEntity> getOldest(int limit);

    @Query("SELECT COUNT(*) FROM pending_events")
    int count();

    @Query("SELECT COUNT(*) FROM pending_events")
    LiveData<Integer> observeCount();

    @Query("DELETE FROM pending_events WHERE event_id IN (:eventIds)")
    void deleteByIds(List<String> eventIds);

    @Query(
            "UPDATE pending_events SET retry_count = retry_count + 1, "
                    + "last_error = :lastError WHERE event_id = :eventId"
    )
    void markFailed(String eventId, String lastError);

    @Query(
            "UPDATE pending_events SET retry_count = retry_count + 1, "
                    + "last_error = :lastError WHERE event_id IN (:eventIds)"
    )
    void markFailedByIds(List<String> eventIds, String lastError);
}
