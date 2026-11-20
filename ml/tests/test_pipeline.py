import sys
import unittest
from pathlib import Path
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from features import PublicSuffixList, parse_url, url_features, normalize_text, template_key, FEATURE_NAMES
from data_builders import bucket
from train import operating_threshold, assert_isolated

class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.psl = PublicSuffixList("com\norg\nco.in\nio\ngithub.io\n*.ck\n!www.ck")

    def test_registered_domain_boundaries(self):
        self.assertEqual("evil.xyz", self.psl.registrable("youtube.com.evil.xyz"))
        self.assertEqual("tenant.github.io", self.psl.registrable("tenant.github.io"))
        self.assertEqual("sbi.co.in", self.psl.registrable("online.sbi.co.in"))
        self.assertEqual("www.ck", self.psl.registrable("www.ck"))

    def test_features_have_a_fixed_order_and_float32_dtype(self):
        vector = url_features(parse_url("https://google.com.evil.xyz/verify?otp=12"), self.psl)
        self.assertEqual(25, len(FEATURE_NAMES))
        self.assertEqual(len(FEATURE_NAMES), len(vector))
        self.assertEqual(np.float32, vector.dtype)
        self.assertEqual(1, vector[FEATURE_NAMES.index("brand_subdomain")])
        self.assertEqual(1, vector[FEATURE_NAMES.index("credential_path")])

    def test_credential_deception_parses_actual_host(self):
        self.assertEqual("evil.xyz", parse_url("https://google.com@evil.xyz").host)
        self.assertIsNone(parse_url("javascript:alert(1)"))
        self.assertIsNone(parse_url("http://999.1.1.1"))

    def test_template_variants_cannot_cross_partitions(self):
        first = template_key("Pay 100 now at https://one.example/abc")
        second = template_key("Pay 200 now at https://two.example/def")
        self.assertEqual(first, second)
        self.assertEqual(bucket("text:" + first), bucket("text:" + second))

    def test_normalization_preserves_indic_text_and_codepoints(self):
        self.assertEqual("अभी ਖੋਲ੍ਹੋ", normalize_text("  अभी\\tਖੋਲ੍ਹੋ ".replace("\\t", "\t")))
        self.assertEqual(4096, len(normalize_text("🙂" * 5000)))

    def test_threshold_respects_false_positive_budget(self):
        labels = np.array([0] * 1000 + [1] * 100)
        scores = np.linspace(0, 1, 1100)
        threshold = operating_threshold(labels, scores, target_fpr=.01, minimum=0)
        self.assertLessEqual(np.mean(scores[:1000] >= threshold), .01)
        fixed = np.zeros(1100, dtype=bool)
        fixed[:5] = True
        threshold = operating_threshold(labels, scores, target_fpr=.01, minimum=0, already_positive=fixed)
        self.assertLessEqual(np.mean((scores >= threshold)[:1000] | fixed[:1000]), .01)

    def test_overlap_audit_fails_on_shared_group(self):
        rows = [{"group":"same","split":s,"label":label} for s in ("train","validation","test") for label in (0,1)]
        with self.assertRaises(AssertionError): assert_isolated(rows)

if __name__ == "__main__":
    unittest.main()
