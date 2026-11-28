import unittest

import bench


class EvidenceTest(unittest.TestCase):
    def test_focused_component_ignores_background_window(self):
        dump = ("  mCurrentFocus=Window{ab u0 com.other/.Main}\n"
                "  mFocusedApp=ActivityRecord{cd u0 com.mitun.sonora/.HomeActivity t4}")
        self.assertEqual(bench.focused_component(dump), "com.other/com.other.Main")
        self.assertIsNone(bench.focused_component("  mCurrentFocus=null"))

    def test_cold_launch_requires_positive_first_frame(self):
        self.assertEqual(bench.cold_time("Status: ok\nLaunchState: COLD\nTotalTime: 273\n"), 273)
        for dump in ("Status: ok\nTotalTime: 0\n",
                     "Status: ok\nLaunchState: WARM\nTotalTime: 273\n",
                     "Error: Activity not started\nTotalTime: 273\n"):
            with self.subTest(dump=dump), self.assertRaises(ValueError):
                bench.cold_time(dump)

    def test_unavailable_frame_counter_is_not_zero(self):
        self.assertIsNone(bench.frame_metrics("Total frames rendered: 120\n")["deadline_misses"])
        with self.assertRaises(ValueError):
            bench.frame_metrics("Total frames rendered: 1\nNumber Frame deadline missed: 2\n")

    def test_android_percentiles_have_attached_units(self):
        dump = ("Total frames rendered: 120\n99th percentile: 27ms\n"
                "90th gpu percentile: 4950ms\n")
        metrics = bench.frame_metrics(dump)
        self.assertEqual(metrics["p99_ms"], 27)
        self.assertIsNone(metrics["gpu_p90_ms"])

    def test_block_median_uses_slot_medians(self):
        self.assertEqual(bench.block_order(["A", "B", "B", "A"]), "ABBA")
        with self.assertRaises(ValueError):
            bench.block_order(["A", "A", "B", "B"])
        self.assertEqual(bench.metric_spread([30, None, 10, 20]), (3, 20, 10, 30))
        self.assertEqual(bench.metric_spread([None]), (0, None, None, None))

    def test_idle_cpu_excludes_the_initial_top_sample(self):
        report = ("PID USER PR NI VIRT RES SHR S[%CPU] %MEM TIME+ ARGS\n"
                  "123 u0_a99 10 -10 1G 110M 80M S 67.0 5.1 00:05 home\n"
                  "PID USER PR NI VIRT RES SHR S[%CPU] %MEM TIME+ ARGS\n"
                  "123 u0_a99 10 -10 1G 110M 80M S 0.2 5.1 00:05 home\n")
        self.assertEqual(bench.idle_cpu(report, "123"), [0.2])

    def test_video_surface_presentation_ignores_pending_frames(self):
        dump = ("16683350\n0 0 0\n7039741645000 7039732559346 7039741645000\n"
                "7039774978000 9223372036854775807 7039774978000\n")
        self.assertEqual(bench.latest_present_ns(dump), 7039732559346)
        self.assertEqual(bench.latest_present_ns("16683350\n0 0 0\n"), 0)


if __name__ == "__main__":
    unittest.main()
