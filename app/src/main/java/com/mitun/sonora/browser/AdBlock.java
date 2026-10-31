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
    private boolean enabled = true;

    public AdBlock(Context context) {
        load(context);
    }

    private void load(Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.adlist)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("|")) {
                    fragments.add(line.substring(1).toLowerCase(Locale.US));
                } else {
                    hosts.add(line.toLowerCase(Locale.US));
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
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
        for (String fragment : fragments) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        String host = hostOf(lower);
        if (host.length() == 0) {
            return false;
        }
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
        return false;
    }

    private static String hostOf(String url) {
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
        // And the port is not part of it either.
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host;
    }
}
