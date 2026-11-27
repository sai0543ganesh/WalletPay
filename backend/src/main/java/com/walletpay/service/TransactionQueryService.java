package com.walletpay.service;

import com.walletpay.dto.ApiModels.AnalyticsResponse;
import com.walletpay.dto.ApiModels.DailySummary;
import com.walletpay.dto.ApiModels.PageResponse;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.entity.User;
import com.walletpay.entity.WalletTransaction;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionQueryService {
    public record Filter(
            String type,
            String direction,
            String status,
            String search,
            LocalDate from,
            LocalDate to) {}

    private final TransactionRepository transactions;
    private final WalletService wallets;
    private final UserRepository users;

    public TransactionQueryService(
            TransactionRepository transactions, WalletService wallets, UserRepository users) {
        this.transactions = transactions;
        this.wallets = wallets;
        this.users = users;
    }

    public PageResponse<TransactionResponse> history(
            Long userId, int page, int size, Filter filter) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100)
            throw ApiException.badRequest(
                    "Page must be nonnegative and size must be between 1 and 100.");
        Page<WalletTransaction> result =
                transactions.findAll(
                        specification(userId, filter),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        preloadCounterparties(result.getContent());
        return new PageResponse<>(
                result.getContent().stream().map(t -> wallets.view(t, userId)).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    public TransactionResponse receipt(Long userId, UUID id) {
        WalletTransaction transaction =
                transactions.findById(id).orElseThrow(ApiException::notFound);
        if (!transaction.getSenderId().equals(userId)
                && !transaction.getRecipientId().equals(userId)) throw ApiException.notFound();
        return wallets.view(transaction, userId);
    }

    public String export(Long userId, Filter filter) {
        Page<WalletTransaction> result =
                transactions.findAll(
                        specification(userId, filter),
                        PageRequest.of(
                                0,
                                10_001,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        if (result.getTotalElements() > 10_000)
            throw ApiException.badRequest(
                    "Export is limited to 10000 rows. Narrow the date range or filters.");
        preloadCounterparties(result.getContent());
        StringBuilder csv =
                new StringBuilder(
                        "Reference,Created at,Type,Direction,Status,Amount"
                                + " (paise),Currency,Counterparty,Phone,Note\r\n");
        for (WalletTransaction entity : result.getContent()) {
            TransactionResponse t = wallets.view(entity, userId);
            List<String> values =
                    List.of(
                            t.reference(),
                            t.createdAt().toString(),
                            t.type(),
                            t.direction(),
                            t.status(),
                            Long.toString(t.amountMinor()),
                            t.currency(),
                            Objects.toString(t.counterpartyName(), ""),
                            Objects.toString(t.counterpartyPhone(), ""),
                            t.note());
            csv.append(
                            values.stream()
                                    .map(TransactionQueryService::csvCell)
                                    .reduce((a, b) -> a + "," + b)
                                    .orElse(""))
                    .append("\r\n");
        }
        return csv.toString();
    }

    public static String csvCell(String input) {
        String value = Objects.toString(input, "");
        String inspected = value.stripLeading();
        if (!inspected.isEmpty() && "=+-@\t\r\n".indexOf(inspected.charAt(0)) >= 0)
            value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public AnalyticsResponse analytics(Long userId, int days) {
        if (days < 1 || days > 366)
            throw ApiException.badRequest("Days must be between 1 and 366.");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(days - 1L);
        Specification<WalletTransaction> completed =
                (root, query, cb) ->
                        cb.and(
                                cb.equal(root.get("status"), "COMPLETED"),
                                cb.or(
                                        cb.equal(root.get("senderId"), userId),
                                        cb.equal(root.get("recipientId"), userId)),
                                cb.greaterThanOrEqualTo(
                                        root.get("completedAt"),
                                        first.atStartOfDay(ZoneOffset.UTC).toInstant()),
                                cb.lessThan(
                                        root.get("completedAt"),
                                        today.plusDays(1)
                                                .atStartOfDay(ZoneOffset.UTC)
                                                .toInstant()));
        List<WalletTransaction> rows = transactions.findAll(completed);
        TreeMap<LocalDate, long[]> daily = new TreeMap<>();
        for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1))
            daily.put(date, new long[2]);
        long income = 0, spending = 0, count = 0;
        for (WalletTransaction t : rows) {
            boolean debit = t.getType().equals("TRANSFER") && t.getSenderId().equals(userId);
            LocalDate day = t.getCompletedAt().atOffset(ZoneOffset.UTC).toLocalDate();
            if (debit) {
                spending = Math.addExact(spending, t.getAmountMinor());
                daily.get(day)[1] = Math.addExact(daily.get(day)[1], t.getAmountMinor());
            } else {
                income = Math.addExact(income, t.getAmountMinor());
                daily.get(day)[0] = Math.addExact(daily.get(day)[0], t.getAmountMinor());
            }
            if (t.getType().equals("TRANSFER")) count++;
        }
        return new AnalyticsResponse(
                days,
                income,
                spending,
                count,
                daily.entrySet().stream()
                        .map(e -> new DailySummary(e.getKey(), e.getValue()[0], e.getValue()[1]))
                        .toList());
    }

    private Specification<WalletTransaction> specification(Long userId, Filter filter) {
        String type = option(filter.type(), Set.of("TRANSFER", "DEPOSIT", "DEMO_CREDIT"));
        String direction = option(filter.direction(), Set.of("CREDIT", "DEBIT"));
        String status = option(filter.status(), Set.of("PENDING", "COMPLETED"));
        if (filter.search() != null && filter.search().length() > 80)
            throw ApiException.badRequest("Search must contain no more than 80 characters.");
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))
            throw ApiException.badRequest("From date must not be after to date.");
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(
                    cb.or(
                            cb.equal(root.get("senderId"), userId),
                            cb.equal(root.get("recipientId"), userId)));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if ("DEBIT".equals(direction))
                predicates.add(
                        cb.and(
                                cb.equal(root.get("type"), "TRANSFER"),
                                cb.equal(root.get("senderId"), userId)));
            if ("CREDIT".equals(direction))
                predicates.add(cb.equal(root.get("recipientId"), userId));
            if (filter.from() != null)
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                filter.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
            if (filter.to() != null)
                predicates.add(
                        cb.lessThan(
                                root.get("createdAt"),
                                filter.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            if (filter.search() != null && !filter.search().isBlank()) {
                String search =
                        "%"
                                + filter.search()
                                        .strip()
                                        .toLowerCase(Locale.ROOT)
                                        .replace("\\", "\\\\")
                                        .replace("%", "\\%")
                                        .replace("_", "\\_")
                                + "%";
                var users = query.subquery(Long.class);
                var person = users.from(User.class);
                users.select(person.get("id"))
                        .where(
                                cb.or(
                                        cb.like(cb.lower(person.get("fullName")), search, '\\'),
                                        cb.like(person.get("phone"), search, '\\')));
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("note")), search, '\\'),
                                cb.like(cb.lower(root.get("reference")), search, '\\'),
                                root.get("senderId").in(users),
                                root.get("recipientId").in(users)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void preloadCounterparties(List<WalletTransaction> rows) {
        Set<Long> ids = new HashSet<>();
        rows.stream()
                .filter(row -> row.getType().equals("TRANSFER"))
                .forEach(
                        row -> {
                            ids.add(row.getSenderId());
                            ids.add(row.getRecipientId());
                        });
        // Populate the request persistence context once, avoiding a separate user query per export
        // row.
        if (!ids.isEmpty()) users.findAllById(ids);
    }

    private static String option(String input, Set<String> allowed) {
        if (input == null || input.equals("ALL") || input.isBlank()) return null;
        if (!allowed.contains(input))
            throw ApiException.badRequest("An unsupported transaction filter was supplied.");
        return input;
    }
}
