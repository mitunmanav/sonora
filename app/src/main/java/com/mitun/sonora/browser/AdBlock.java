package com.mitun.sonora.browser;

import android.content.Context;

import com.mitun.sonora.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A hosts-style ad blocker.
 *
 * The list is bundled in the app and read once. Nothing is fetched to update
 * it, so the browser never makes a request just to decide whether to make
 * another one.
 */
public final class AdBlock {
    private final Set<String> hosts = new HashSet<>();
    private final List<String> fragments = new ArrayList<>();
    private volatile boolean enabled = true;

    public AdBlock(Context context) {
        load(context);
    }

    /** Tests build the list line by line instead of reading the resource. */
    AdBlock() {
    }

    private void load(Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.adlist)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                addLine(line);
            }
        } catch (IOException | RuntimeException ignored) {
        }
        // A missing or corrupt list is a blocker that blocks nothing.
        if (size() == 0) {
            enabled = false;
        }
    }

    void addLine(String line) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
            return;
        }
        if (line.startsWith("|")) {
            String fragment = line.substring(1).toLowerCase(Locale.US);
            if (fragment.length() > 0) {
                fragments.add(fragment);
            }
            return;
        }
        // A hosts line can carry an address first: "0.0.0.0 ads.example".
        String[] parts = line.split("\\s+");
        hosts.add(parts[parts.length - 1].toLowerCase(Locale.US));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value && size() > 0;
    }

    public int size() {
        return hosts.size() + fragments.size();
    }

    /** True when the request should be blocked. */
    public boolean isBlocked(String url) {
        if (!enabled || url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        String host = hostOf(lower);
        if (host.length() > 0) {
            // A blocked host blocks its subdomains too, the way a hosts file does.
            String domain = host;
            while (domain.length() > 0) {
                if (hosts.contains(domain)) {
                    return true;
                }
                int dot = domain.indexOf('.');
                if (dot < 0) {
                    break;
                }
                domain = domain.substring(dot + 1);
            }
        }
        for (String fragment : fragments) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static String hostOf(String url) {
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : scheme + 3;
        int end = url.indexOf('/', start);
        String host = end < 0 ? url.substring(start) : url.substring(start, end);
        // The userinfo comes first: "user:pass@ads.example" is the host
        // ads.example, not "user".
        int at = host.indexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        // A query or a fragment is not part of the host.
        int cut = host.indexOf('?');
        if (cut < 0) {
            cut = host.indexOf('#');
        }
        if (cut >= 0) {
            host = host.substring(0, cut);
        }
        // An IPv6 literal keeps its colons: "[2001:db8::1]:8080" is "2001:db8::1".
        if (host.startsWith("[") && host.indexOf(']') > 0) {
            host = host.substring(1, host.indexOf(']'));
        } else {
            // And the port is not part of it either.
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        // A fully-qualified name ends in a dot; the list spells it without.
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }
}
