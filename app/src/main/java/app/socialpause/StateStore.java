package app.socialpause;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Base64;
import java.io.*;
import app.socialpause.engine.RulesEngine;

/** Private, offline state. No exported input is deserialized. Schema changes must migrate/reset this key. */
final class StateStore {
    private final SharedPreferences prefs;
    private final int boot;
    private String previous = "";
    StateStore(Context context) {
        prefs = context.getSharedPreferences("socialpause-v1", Context.MODE_PRIVATE);
        boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    RulesEngine read() {
        try {
            String data = prefs.getString("engine", "");
            try (var in = new ObjectInputStream(new ByteArrayInputStream(Base64.decode(data, Base64.NO_WRAP)))) {
                RulesEngine result = (RulesEngine) in.readObject();
                int savedBoot = prefs.getInt("boot", -1);
                // Only a confirmed boot change discards a running session. Missing evidence
                // must not turn ordinary process recreation into a Stop-lock bypass.
                boolean rebooted = boot >= 0 && savedBoot >= 0 && boot != savedBoot;
                result.attach(!rebooted);
                return result;
            }
        } catch (IOException | ClassNotFoundException | RuntimeException ignored) {
            return new RulesEngine(); // First install or incompatible saved state starts stopped.
        }
    }
    void save(RulesEngine engine) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) { out.writeObject(engine); }
            String encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
            if (!encoded.equals(previous)) {
                prefs.edit().putString("engine", encoded).putInt("boot", boot).apply(); previous = encoded;
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot save timing state", e); }
    }
}
