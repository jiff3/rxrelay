package dev.rxrelay.core.service;

import dev.rxrelay.core.domain.*;
import dev.rxrelay.core.repository.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WatchlistService {
  private final WatchlistRepository watchlists;
  private final WatchlistItemRepository items;
  private final MedicationRepository medications;
  private final NotificationRepository notifications;
  private final AuditEventRepository audit;
  private final Clock clock;

  public WatchlistService(
      WatchlistRepository watchlists,
      WatchlistItemRepository items,
      MedicationRepository medications,
      NotificationRepository notifications,
      AuditEventRepository audit,
      Clock clock) {
    this.watchlists = watchlists;
    this.items = items;
    this.medications = medications;
    this.notifications = notifications;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<Watchlist> list(String owner, int page, int size) {
    return watchlists.findByOwnerId(
        owner,
        PageRequest.of(
            page, Math.min(size, 50), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
  }

  @Transactional(readOnly = true)
  public WatchlistResult get(String owner, UUID id, int itemPage, int itemSize) {
    Watchlist watchlist = requireOwned(owner, id);
    Page<WatchlistItem> page =
        items.findByWatchlistId(
            id,
            PageRequest.of(
                itemPage,
                Math.min(itemSize, 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
    return new WatchlistResult(watchlist, page, page.getTotalElements());
  }

  public Watchlist create(String owner, String rawName) {
    String name = rawName.strip();
    if (watchlists.existsByOwnerIdAndNameIgnoreCase(owner, name)) {
      throw new ConflictException("A watchlist with this name already exists");
    }
    Watchlist saved = watchlists.save(new Watchlist(owner, name, clock.instant()));
    audit.save(
        AuditEvent.applicationEvent(
            "WatchlistCreated",
            "WATCHLIST",
            saved.getId(),
            "Demo watchlist created",
            clock.instant()));
    return saved;
  }

  public WatchlistItem addItem(String owner, UUID watchlistId, UUID medicationId) {
    Watchlist watchlist = requireOwned(owner, watchlistId);
    if (items.existsByWatchlistIdAndMedicationId(watchlistId, medicationId)) {
      throw new ConflictException("Drug is already on this watchlist");
    }
    Medication medication =
        medications
            .findById(medicationId)
            .orElseThrow(() -> new NotFoundException("Drug not found"));
    WatchlistItem saved = items.save(new WatchlistItem(watchlist, medication, clock.instant()));
    audit.save(
        AuditEvent.applicationEvent(
            "WatchlistItemAdded",
            "WATCHLIST_ITEM",
            saved.getId(),
            "Drug added to demo watchlist",
            clock.instant()));
    return saved;
  }

  public void removeItem(String owner, UUID watchlistId, UUID itemId) {
    requireOwned(owner, watchlistId);
    WatchlistItem item =
        items
            .findByIdAndWatchlistId(itemId, watchlistId)
            .orElseThrow(() -> new NotFoundException("Watchlist item not found"));
    items.delete(item);
    audit.save(
        AuditEvent.applicationEvent(
            "WatchlistItemRemoved",
            "WATCHLIST_ITEM",
            itemId,
            "Drug removed from demo watchlist",
            clock.instant()));
  }

  public void delete(String owner, UUID watchlistId) {
    Watchlist watchlist = requireOwned(owner, watchlistId);
    watchlists.delete(watchlist);
    audit.save(
        AuditEvent.applicationEvent(
            "WatchlistDeleted",
            "WATCHLIST",
            watchlistId,
            "Demo watchlist deleted",
            clock.instant()));
  }

  @Transactional(readOnly = true)
  public Page<Notification> notifications(String owner, boolean unreadOnly, int page, int size) {
    PageRequest request =
        PageRequest.of(
            page, Math.min(size, 50), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    return unreadOnly
        ? notifications.findByOwnerIdAndReadFalse(owner, request)
        : notifications.findByOwnerId(owner, request);
  }

  public Notification markRead(String owner, UUID id) {
    Notification notification =
        notifications
            .findByIdAndOwnerId(id, owner)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
    if (!notification.isRead()) {
      notification.markRead();
      audit.save(
          AuditEvent.applicationEvent(
              "NotificationRead",
              "NOTIFICATION",
              id,
              "Demo notification marked read",
              clock.instant()));
    }
    return notification;
  }

  public long itemCount(UUID watchlistId) {
    return items.countByWatchlistId(watchlistId);
  }

  private Watchlist requireOwned(String owner, UUID id) {
    return watchlists
        .findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> new NotFoundException("Watchlist not found"));
  }

  public record WatchlistResult(Watchlist watchlist, Page<WatchlistItem> items, long itemCount) {}
}
