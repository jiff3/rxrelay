package dev.rxrelay.core.repository;

import dev.rxrelay.core.domain.ProcessedEvent;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
  Page<ProcessedEvent> findByProcessingState(String processingState, Pageable pageable);

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events (event_id, event_type, ingestion_run_id, source_record_id, "
              + "schema_version, producer, correlation_id, processing_state, received_at, retry_count, "
              + "source_topic, source_partition, source_offset) VALUES (:eventId, :eventType, :runId, "
              + ":sourceRecordId, :schemaVersion, :producer, :correlationId, 'PROCESSING', :receivedAt, 0, "
              + ":sourceTopic, :sourcePartition, :sourceOffset) ON CONFLICT (event_id) DO UPDATE SET "
              + "processing_state = 'PROCESSING', event_type = EXCLUDED.event_type, "
              + "ingestion_run_id = EXCLUDED.ingestion_run_id, source_record_id = EXCLUDED.source_record_id, "
              + "schema_version = EXCLUDED.schema_version, producer = EXCLUDED.producer, "
              + "correlation_id = EXCLUDED.correlation_id, source_topic = EXCLUDED.source_topic, "
              + "source_partition = EXCLUDED.source_partition, source_offset = EXCLUDED.source_offset "
              + "WHERE processed_events.processing_state = 'RETRYING'",
      nativeQuery = true)
  int claim(
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("runId") java.util.UUID runId,
      @Param("sourceRecordId") String sourceRecordId,
      @Param("schemaVersion") String schemaVersion,
      @Param("producer") String producer,
      @Param("correlationId") String correlationId,
      @Param("receivedAt") Instant receivedAt,
      @Param("sourceTopic") String sourceTopic,
      @Param("sourcePartition") int sourcePartition,
      @Param("sourceOffset") long sourceOffset);

  @Modifying
  @Query(
      "update ProcessedEvent e set e.processingState = 'PROCESSED', e.processedAt = :processedAt, "
          + "e.lastErrorCode = null where e.eventId = :eventId")
  int markProcessed(@Param("eventId") String eventId, @Param("processedAt") Instant processedAt);

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events (event_id, event_type, schema_version, producer, correlation_id, "
              + "processing_state, received_at, retry_count, last_error_code, source_topic, source_partition, "
              + "source_offset) VALUES (:eventId, :eventType, :schemaVersion, :producer, :correlationId, "
              + "'RETRYING', :receivedAt, :retryCount, :errorCode, :sourceTopic, :sourcePartition, :sourceOffset) "
              + "ON CONFLICT (event_id) DO UPDATE SET processing_state = CASE WHEN "
              + "processed_events.processing_state = 'PROCESSED' THEN 'PROCESSED' ELSE 'RETRYING' END, "
              + "retry_count = GREATEST(processed_events.retry_count, EXCLUDED.retry_count), "
              + "last_error_code = EXCLUDED.last_error_code",
      nativeQuery = true)
  int recordRetry(
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("schemaVersion") String schemaVersion,
      @Param("producer") String producer,
      @Param("correlationId") String correlationId,
      @Param("receivedAt") Instant receivedAt,
      @Param("retryCount") int retryCount,
      @Param("errorCode") String errorCode,
      @Param("sourceTopic") String sourceTopic,
      @Param("sourcePartition") int sourcePartition,
      @Param("sourceOffset") long sourceOffset);

  @Modifying
  @Query(
      value =
          "INSERT INTO processed_events (event_id, event_type, schema_version, producer, correlation_id, "
              + "processing_state, received_at, retry_count, dead_lettered_at, dead_letter_topic, "
              + "last_error_code, source_topic, source_partition, source_offset) VALUES (:eventId, :eventType, "
              + ":schemaVersion, :producer, :correlationId, 'DEAD_LETTERED', :receivedAt, :retryCount, "
              + ":deadLetteredAt, :deadLetterTopic, :errorCode, :sourceTopic, :sourcePartition, :sourceOffset) "
              + "ON CONFLICT (event_id) DO UPDATE SET processing_state = CASE WHEN "
              + "processed_events.processing_state = 'PROCESSED' THEN 'PROCESSED' ELSE 'DEAD_LETTERED' END, "
              + "retry_count = GREATEST(processed_events.retry_count, EXCLUDED.retry_count), "
              + "dead_lettered_at = EXCLUDED.dead_lettered_at, dead_letter_topic = EXCLUDED.dead_letter_topic, "
              + "last_error_code = EXCLUDED.last_error_code",
      nativeQuery = true)
  int recordDeadLetter(
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("schemaVersion") String schemaVersion,
      @Param("producer") String producer,
      @Param("correlationId") String correlationId,
      @Param("receivedAt") Instant receivedAt,
      @Param("retryCount") int retryCount,
      @Param("deadLetteredAt") Instant deadLetteredAt,
      @Param("deadLetterTopic") String deadLetterTopic,
      @Param("errorCode") String errorCode,
      @Param("sourceTopic") String sourceTopic,
      @Param("sourcePartition") int sourcePartition,
      @Param("sourceOffset") long sourceOffset);
}
