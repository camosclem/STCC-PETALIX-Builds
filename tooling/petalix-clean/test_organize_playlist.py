import unittest
from organize_playlist import organize, classify, foreign, on_demand, attrs_and_name


def sample(channels):
    return "#EXTM3U\n" + "\n".join(
        '#EXTINF:-1 tvg-id="' + str(i) + '" group-title="' + group + '",' + name
        + "\n" + url
        for i, (group, name, url) in enumerate(channels, 1)
    ) + "\n"


def stream(i):
    return "https://example.invalid/live/" + str(i) + ".ts"


class LineupTests(unittest.TestCase):
    def test_one_sports_group_major_first_then_fanduel_tsn_then_others(self):
        src = sample([
            ("USA | Sports", "NFL Sunday Ticket 4K", stream(1)),
            ("USA | Sports", "FanDuel Sports Network 5 HD", stream(2)),
            ("USA | Sports", "ESPN2 HD", stream(3)),
            ("USA | Sports", "ESPN 4K", stream(4)),
            ("Canada | Sports", "TSN 3 FHD", stream(5)),
            ("USA | Sports", "FANDUEL 1 UHD", stream(6)),
            ("Canada | Sports", "TSN 1 HD", stream(7)),
            ("USA | Sports", "NASCAR 4K", stream(8)),
        ])
        output, stats = organize(src)
        names = [attrs_and_name(line)[1] for line in output.splitlines() if line.startswith("#EXTINF")]
        self.assertEqual(
            names,
            ["ESPN", "ESPN2", "FANDUEL 1", "FanDuel Sports Network 5",
             "TSN 1", "TSN 3", "NASCAR", "NFL Sunday Ticket"]
        )
        self.assertEqual(output.count('group-title="Sports"'), 8)
        self.assertNotIn("Sports |", output)

    def test_highest_quality_no_backups_or_duplicates(self):
        src = sample([
            ("USA | Sports", "ESPN HD", stream(1)),
            ("USA | Sports", "ESPN FHD", stream(2)),
            ("USA | Sports", "ESPN 4K", stream(3)),
            ("USA | Sports", "ESPN SD", stream(4)),
            ("USA | Sports", "FANDUEL 1 FHD", stream(5)),
            ("USA | Sports", "FanDuel Sports Network 1 UHD", stream(6)),
        ])
        output, stats = organize(src, include_backups=True)
        self.assertEqual(stats["primary_channels"], 2)
        self.assertEqual(stats["duplicates_removed"], 4)
        self.assertEqual(stats["backup_channels"], 0)
        self.assertNotIn("Backup Streams", output)
        self.assertIn(stream(3), output)
        self.assertIn(stream(6), output)
        self.assertNotIn(stream(2) + "\n", output)

    def test_international_hidden_except_tsn_1_to_5(self):
        src = sample([
            ("Canada | News", "Toronto News", stream(1)),
            ("UK | Sports", "London Sports", stream(2)),
            ("Canada | Sports", "TSN 1 FHD", stream(3)),
            ("Canada | Sports", "TSN 6 FHD", stream(4)),
            ("Canada | Sports", "TSN 5 HD", stream(5)),
            ("USA | News", "America News HD", stream(6)),
        ])
        output, stats = organize(src)
        self.assertEqual(stats["primary_channels"], 3)
        self.assertNotIn("London", output)
        self.assertNotIn("Toronto News", output)
        self.assertNotIn("TSN 6", output)
        self.assertIn("TSN 1", output)
        self.assertIn("TSN 5", output)

    def test_vod_not_shown_in_live_and_linear_movies_remain(self):
        src = sample([
            ("USA | VOD", "Film One", stream(1)),
            ("USA | TV Shows", "Episode 1", stream(2)),
            ("USA | Movies", "Film Two", "https://example.invalid/movie/3.ts"),
            ("USA | Sports", "Sports Replay", "https://example.invalid/video.mp4"),
            ("USA | Movies", "HBO FHD", stream(5)),
            ("USA | 24/7 Movies", "24/7 Action HD", stream(6))
        ])
        output, stats = organize(src)
        self.assertEqual(stats["primary_channels"], 2)
        self.assertIn("HBO", output)
        self.assertIn("24/7 Action", output)
        self.assertNotIn("Film One", output)
        self.assertNotIn("Episode", output)
        self.assertNotIn("Film Two", output)

    def test_resolution_first_within_locals_and_news(self):
        src = sample([
            ("USA | Locals", "ABC Charlotte SD", stream(1)),
            ("USA | Locals", "NBC Raleigh FHD", stream(2)),
            ("USA | Locals", "CBS Asheville 4K", stream(3)),
            ("USA | Locals", "ABC Charlotte 4K", stream(4)),
            ("USA | News", "USA Local News HD", stream(5))
        ])
        output, stats = organize(src)
        names = [attrs_and_name(line)[1] for line in output.splitlines() if line.startswith("#EXTINF")]
        self.assertEqual(names, ["ABC Charlotte", "CBS Asheville", "NBC Raleigh", "USA Local News"])
        self.assertEqual(stats["duplicates_removed"], 1)
        self.assertIn(stream(4), output)
        self.assertNotIn(stream(1), output)

    def test_unnumbered_or_beyond_five_fanduel_tsn_removed(self):
        src = sample([
            ("USA | Sports", "Fanduel 1 FHD", stream(1)),
            ("USA | Sports", "Fanduel 5 4K", stream(2)),
            ("USA | Sports", "Fanduel 6 FHD", stream(3)),
            ("USA | Sports", "Fanduel Sports Carolinas HD", stream(4)),
            ("Canada | Sports", "TSN 2 HD", stream(5)),
            ("Canada | Sports", "TSN 7 HD", stream(6))
        ])
        output, stats = organize(src)
        self.assertEqual(stats["primary_channels"], 3)
        self.assertNotIn("Fanduel 6", output)
        self.assertNotIn("Fanduel Sports Carolinas", output)
        self.assertNotIn("TSN 7", output)
        self.assertIn("TSN 2", output)

    def test_preserves_extinf_attributes_and_m3u_header(self):
        src = '#EXTM3U url-tvg="https://example.invalid/xmltv"\n' + \
              '#EXTINF:-1 tvg-id="espn.us" tvg-logo="https://example.invalid/logo" group-title="Sports",ESPN 4K\n' + \
              stream(1) + '\n'
        output, stats = organize(src)
        self.assertIn('tvg-id="espn.us"', output)
        self.assertIn('tvg-logo="https://example.invalid/logo"', output)
        self.assertTrue(output.startswith('#EXTM3U url-tvg="https://example.invalid/xmltv"'))
        self.assertEqual(stats["primary_channels"], 1)


if __name__ == "__main__":
    unittest.main()
