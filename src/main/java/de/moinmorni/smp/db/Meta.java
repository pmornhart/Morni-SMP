package de.moinmorni.smp.db;

/** Kleine Merkzettel in der Datenbank (z. B. Bau-Fassung und Höhe der Stadt). */
public final class Meta {

    private final Database db;

    public Meta(Database db) {
        this.db = db;
    }

    public String get(String key) {
        return db.tx(c -> Database.one(c, "SELECT value FROM meta WHERE key = ?", rs -> rs.getString(1), key));
    }

    public int getInt(String key, int fallback) {
        String v = get(key);
        try {
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void set(String key, Object value) {
        db.run(c -> Database.update(c, "INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", key,
                String.valueOf(value)));
    }
}
