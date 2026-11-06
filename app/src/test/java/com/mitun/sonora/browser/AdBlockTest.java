package com.mitun.sonora.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdBlockTest {

    private static AdBlock blocker(String... lines) {
        AdBlock block = new AdBlock();
        for (String line : lines) {
            block.addLine(line);
        }
        block.setEnabled(true);
        return block;
    }

    @Test
    public void blockedHostAlsoBlocksItsSubdomains() {
        AdBlock block = blocker("doubleclick.net");
        assertTrue(block.isBlocked("https://doubleclick.net/pixel"));
        assertTrue(block.isBlocked("https://ads.doubleclick.net/pixel"));
        assertTrue(block.isBlocked("https://a.b.doubleclick.net/"));
    }

    @Test
    public void aNameThatEndsTheSameIsNotASubdomain() {
        AdBlock block = blocker("doubleclick.net");
        assertFalse(block.isBlocked("https://notdoubleclick.net/"));
        assertFalse(block.isBlocked("https://doubleclick.net.example.com/"));
    }

    @Test
    public void userinfoIsNotPartOfTheHost() {
        AdBlock block = blocker("ads.example.com");
        assertTrue(block.isBlocked("http://user:pass@ads.example.com/x"));
        assertFalse(block.isBlocked("http://ads.example.com@clean.example.org/"));
    }

    @Test
    public void ipv6LiteralsKeepTheirColons() {
        AdBlock block = blocker("2001:db8::1");
        assertTrue(block.isBlocked("http://[2001:db8::1]/x"));
        assertTrue(block.isBlocked("http://[2001:db8::1]:8080/x"));
        assertFalse(block.isBlocked("http://[2001:db8::2]/x"));
    }

    @Test
    public void aTrailingDotStillMatches() {
        AdBlock block = blocker("ads.example.com");
        assertTrue(block.isBlocked("http://ads.example.com./x"));
    }

    @Test
    public void thePortIsNotPartOfTheHost() {
        AdBlock block = blocker("ads.example.com");
        assertTrue(block.isBlocked("http://ads.example.com:8080/x"));
        assertTrue(block.isBlocked("http://ads.example.com?x=1"));
    }

    @Test
    public void fragmentsMatchAnywhereInTheUrl() {
        AdBlock block = blocker("|/ads/");
        assertTrue(block.isBlocked("http://clean.example.org/ads/banner.png"));
        assertFalse(block.isBlocked("http://clean.example.org/story"));
    }

    @Test
    public void matchingIgnoresCase() {
        AdBlock block = blocker("Ads.Example.Com");
        assertTrue(block.isBlocked("HTTP://ADS.EXAMPLE.COM/X"));
    }

    @Test
    public void aBareFragmentMarkerBlocksNothing() {
        AdBlock block = blocker("|");
        assertFalse(block.isBlocked("http://anything.example.org/"));
    }

    @Test
    public void hostsStyleLinesKeepTheLastToken() {
        AdBlock block = blocker("0.0.0.0 ads.example.com");
        assertTrue(block.isBlocked("http://ads.example.com/"));
        assertFalse(block.isBlocked("http://0.0.0.0/"));
    }

    @Test
    public void anEmptyListStaysOff() {
        AdBlock block = new AdBlock();
        block.setEnabled(true);
        assertFalse(block.isEnabled());
        assertFalse(block.isBlocked("http://ads.example.com/"));
    }

    @Test
    public void aDisabledBlockerBlocksNothing() {
        AdBlock block = blocker("ads.example.com");
        block.setEnabled(false);
        assertFalse(block.isBlocked("http://ads.example.com/"));
    }
}
