import unittest
from organize_playlist import organize, classify, foreign, attrs_and_name


def sample(channels):
    return "#EXTM3U\n" + "\n".join(
        '#EXTINF:-1 tvg-id="' + str(i) + '" group-title="' + group + '",' + name
        + "\nhttps://example.invalid/channel/" + str(i)
        for i, (group, name) in enumerate(channels, 1)
    ) + "\n"


class LineupTests(unittest.TestCase):
    def test_sports_priority_and_fanduel(self):
        self.assertEqual(classify("USA | Sports", "ESPN HD"), "Sports | Major Networks")
        self.assertEqual(classify("USA | Sports", "FanDuel Sports Network Carolinas"), "Sports | FanDuel")
        self.assertEqual(classify("Canada | Sports", "TSN+ Events 1"), "Sports | TSN+")
        self.assertEqual(classify("USA | Sports | NFL", "Carolina Panthers"), "Sports | NFL")
        self.assertEqual(classify("USA | Sports | NBA", "Boston Celtics"), "Sports | NBA")
        self.assertEqual(classify("USA | Sports", "NASCAR Live"), "Sports | Racing")

    def test_non_us_removed_with_explicit_tsn_plus_exception(self):
        self.assertTrue(foreign("Canada | News", "Toronto News"))
        self.assertFalse(foreign("Canada | Sports", "TSN+ Live Event"))
        self.assertFalse(foreign("USA | Locals", "ABC Charlotte"))

    def test_dedupe_selects_higher_quality_keeps_ids(self):
        src = sample([
            ("USA | Sports", "ESPN SD"),
            ("USA | Sports", "ESPN FHD"),
            ("Canada | News", "Toronto News"),
            ("USA | Locals", "ABC Charlotte"),
            ("USA | 24/7 | DRAMA", "24/7 Mayday"),
            ("USA | Sports", "FanDuel Sports Network")
        ])
        output, stats = organize(src)
        self.assertEqual(stats["primary_channels"], 4)
        self.assertEqual(stats["duplicates_condensed"], 1)
        self.assertEqual(stats["foreign_hidden"], 1)
        self.assertIn('tvg-id="2"', output)
        self.assertNotIn('tvg-id="1"', output)
        self.assertIn('group-title="Sports | Major Networks",ESPN', output)
        self.assertIn('group-title="Sports | FanDuel"', output)
        self.assertIn('group-title="Locals"', output)
        self.assertIn('group-title="24/7 | TV & Series"', output)

    def test_backups_optional(self):
        src = sample([("USA | Sports", "ESPN HD"), ("USA | Sports", "ESPN SD")])
        plain, _ = organize(src)
        with_backups, _ = organize(src, include_backups=True)
        self.assertNotIn("Backup Streams", plain)
        self.assertIn("Backup Streams", with_backups)

    def test_preserves_extinf_attributes(self):
        src = "#EXTM3U\n#EXTINF:-1 tvg-id=\"espn.us\" tvg-logo=\"x\" group-title=\"Sports\",ESPN FHD\nhttps://example.invalid/1\n"
        output, stats = organize(src)
        self.assertIn('tvg-id="espn.us"', output)
        self.assertIn('tvg-logo="x"', output)
        self.assertEqual(stats["primary_channels"], 1)


if __name__ == "__main__":
    unittest.main()
