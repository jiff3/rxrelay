package dev.rxrelay.core.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.rxrelay.core.domain.Watchlist;
import dev.rxrelay.core.domain.WatchlistItem;
import dev.rxrelay.core.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WatchlistServiceTest {
  private final WatchlistRepository watchlists = mock(WatchlistRepository.class);
  private final WatchlistItemRepository items = mock(WatchlistItemRepository.class);
  private final MedicationRepository medications = mock(MedicationRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final AuditEventRepository audit = mock(AuditEventRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC);
  private WatchlistService service;

  @BeforeEach
  void setUp() {
    service = new WatchlistService(watchlists, items, medications, notifications, audit, clock);
  }

  @Test
  void duplicateWatchlistNameIsAConflict() {
    when(watchlists.existsByOwnerIdAndNameIgnoreCase("demo", "Critical")).thenReturn(true);
    assertThatThrownBy(() -> service.create("demo", " Critical "))
        .isInstanceOf(ConflictException.class);
    verify(watchlists, never()).save(any());
  }

  @Test
  void duplicateItemIsAConflictBeforeWrite() {
    UUID watchlistId = UUID.randomUUID();
    UUID medicationId = UUID.randomUUID();
    when(watchlists.findByIdAndOwnerId(watchlistId, "demo"))
        .thenReturn(Optional.of(new Watchlist("demo", "Critical", clock.instant())));
    when(items.existsByWatchlistIdAndMedicationId(watchlistId, medicationId)).thenReturn(true);
    assertThatThrownBy(() -> service.addItem("demo", watchlistId, medicationId))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(medications);
  }

  @Test
  void missingItemCannotBeSilentlyRemoved() {
    UUID watchlistId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    when(watchlists.findByIdAndOwnerId(watchlistId, "demo"))
        .thenReturn(Optional.of(new Watchlist("demo", "Critical", clock.instant())));
    when(items.findByIdAndWatchlistId(itemId, watchlistId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.removeItem("demo", watchlistId, itemId))
        .isInstanceOf(NotFoundException.class);
    verify(items, never()).delete(any(WatchlistItem.class));
  }

  @Test
  void deletingOwnedWatchlistAlsoRecordsAuditEvent() {
    UUID watchlistId = UUID.randomUUID();
    Watchlist watchlist = new Watchlist("demo", "Critical", clock.instant());
    when(watchlists.findByIdAndOwnerId(watchlistId, "demo")).thenReturn(Optional.of(watchlist));

    service.delete("demo", watchlistId);

    verify(watchlists).delete(watchlist);
    verify(audit).save(any());
  }
}
