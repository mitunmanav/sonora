# Keep the launcher and dream entry points.
# Without these, R8 strips them and the app has no launcher and no screen saver.
-keep class com.mitun.sonora.HomeActivity { *; }
-keep class com.mitun.sonora.dream.SonoraDreamService { *; }

# Keep the tool activities and the browser, which are only reached from code
# and the manifest but have no framework-visible entry points of their own.
-keep class com.mitun.sonora.tools.** { *; }
-keep class com.mitun.sonora.browser.** { *; }
