package inventory;

public class SuggestionLogic {
    
}
package inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import models.Medicine;

/**
 * Builds the drop-down suggestions for the Inventory screen from inventory rows that the
 * existing InventoryDAO already returns. Pure GUI-side logic: no SQL, no DAO changes.
 */
public final class SuggestionLogic {

    private SuggestionLogic() {
    }

    public static final int MIN_CHARS = 2;
    public static final int MAX_HITS = 14;

    /** One suggestion line. */
    public static final class Hit {
        public final Medicine med;       // representative row (latest batch of that medicine)
        public final boolean byBatch;    // true = matched on batch number, false = on medicine name
        public final String text;        // text to put into the box when picked

        Hit(Medicine med, boolean byBatch, String text) {
            this.med = med;
            this.byBatch = byBatch;
            this.text = text;
        }

        public String label() {
            if (byBatch) {
                return med.getBatchNumber() + "   \u2022   " + med.getMedicineName()
                        + "  (stock " + med.getStockQuantity() + ")";
            }
            return med.getMedicineName();
        }
    }

    private static boolean has(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static boolean starts(String value, String lowerQuery) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith(lowerQuery);
    }

    /** Distinct medicine names containing the text; each hit carries that medicine's latest batch. */
    public static List<Hit> nameHits(List<Medicine> rows, String query) {
        List<Hit> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < MIN_CHARS || rows == null) {
            return out;
        }
        Map<String, Medicine> latest = new LinkedHashMap<>();
        for (Medicine m : rows) {
            if (!has(m.getMedicineName(), q)) {
                continue;
            }
            String key = m.getMedicineName().trim().toLowerCase(Locale.ROOT);
            Medicine cur = latest.get(key);
            if (cur == null || m.getMedicineId() > cur.getMedicineId()) {
                latest.put(key, m);
            }
        }
        List<Medicine> names = new ArrayList<>(latest.values());
        names.sort((a, b) -> {
            boolean sa = starts(a.getMedicineName(), q), sb = starts(b.getMedicineName(), q);
            if (sa != sb) {
                return sa ? -1 : 1;                       // names that START with the text first
            }
            return a.getMedicineName().compareToIgnoreCase(b.getMedicineName());
        });
        for (Medicine m : names) {
            out.add(new Hit(m, false, m.getMedicineName().trim()));
            if (out.size() >= MAX_HITS) {
                break;
            }
        }
        return out;
    }

    /** Batch numbers containing the text (one hit per batch row). */
    public static List<Hit> batchHits(List<Medicine> rows, String query) {
        List<Hit> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < MIN_CHARS || rows == null) {
            return out;
        }
        List<Medicine> matches = new ArrayList<>();
        for (Medicine m : rows) {
            if (has(m.getBatchNumber(), q)) {
                matches.add(m);
            }
        }
        matches.sort((a, b) -> {
            boolean sa = starts(a.getBatchNumber(), q), sb = starts(b.getBatchNumber(), q);
            if (sa != sb) {
                return sa ? -1 : 1;
            }
            return a.getBatchNumber().compareToIgnoreCase(b.getBatchNumber());
        });
        for (Medicine m : matches) {
            out.add(new Hit(m, true, m.getBatchNumber().trim()));
            if (out.size() >= MAX_HITS) {
                break;
            }
        }
        return out;
    }

    /** Search box: medicine names first, then batch numbers. */
    public static List<Hit> mixedHits(List<Medicine> rows, String query) {
        List<Hit> out = new ArrayList<>(nameHits(rows, query));
        for (Hit h : batchHits(rows, query)) {
            if (out.size() >= MAX_HITS) {
                break;
            }
            out.add(h);
        }
        return out;
    }

    /** Exact (case-insensitive) match of a typed medicine name: its latest batch, or null. */
    public static Medicine exactMedicine(List<Medicine> rows, String name) {
        if (rows == null || name == null) {
            return null;
        }
        String n = name.trim();
        Medicine best = null;
        for (Medicine m : rows) {
            if (m.getMedicineName() != null && m.getMedicineName().trim().equalsIgnoreCase(n)
                    && (best == null || m.getMedicineId() > best.getMedicineId())) {
                best = m;
            }
        }
        return best;
    }
}
