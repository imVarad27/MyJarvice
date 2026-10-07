import unittest
from connection_health import probe_model, names_match


class ConnectionHealthTests(unittest.TestCase):
    def test_only_list_endpoints_are_used_and_latest_alias_is_resolved(self):
        paths = []
        def fetch(url):
            paths.append(url)
            return {"models": [{"name": "synthetic:latest"}]}
        report = probe_model("synthetic", "http://localhost:11434/api/chat", fetch)
        self.assertEqual(["http://localhost:11434/api/tags", "http://localhost:11434/api/ps"], paths)
        self.assertEqual("installed", report["model_status"])
        self.assertTrue(report["loaded"])

    def test_cold_model_is_not_started(self):
        report = probe_model("synthetic", "http://localhost:11434/api/chat",
                             lambda url: {"models": [{"name": "synthetic"}] if url.endswith("tags") else []})
        self.assertEqual("reachable", report["runtime"])
        self.assertFalse(report["loaded"])

    def test_missing_model_never_probes_loading(self):
        calls = []
        def fetch(url):
            calls.append(url)
            return {"models": []}
        report = probe_model("synthetic", "https://localhost:11434/api/chat", fetch)
        self.assertEqual("missing", report["model_status"])
        self.assertEqual(1, len(calls))
        self.assertIsNone(report["loaded"])

    def test_unreachable_runtime_does_not_echo_exceptions_or_credentials(self):
        def fetch(url):
            raise OSError("private credential / private path")
        report = probe_model("synthetic", "http://localhost:11434/api/chat", fetch)
        self.assertEqual("unreachable", report["runtime"])
        self.assertNotIn("private", str(report))

    def test_ps_failure_preserves_installed_state_without_claiming_loaded(self):
        def fetch(url):
            if url.endswith("ps"):
                raise OSError("Not supported")
            return {"models": [{"model": "synthetic"}]}
        report = probe_model("synthetic", "http://localhost:11434/api/chat", fetch)
        self.assertEqual("installed", report["model_status"])
        self.assertIsNone(report["loaded"])

    def test_malformed_or_excessive_lists_are_not_success(self):
        for payload in ({}, {"models": "wrong"}, {"models": ["model"]},
                        {"models": [{"name": "private\ntext"}]}, {"models": [{"name": "a"}] * 501}):
            report = probe_model("synthetic", "http://localhost:11434/api/chat", lambda _: payload)
            self.assertEqual("invalid_response", report["runtime"])

    def test_bad_configuration_never_makes_a_network_call(self):
        def fetch(url):
            self.fail("Invalid configuration must not trigger a request")
        for url in ("file:///private", "http://user:pass@localhost", "http://localhost?secret=yes", "http://localhost#fragment"):
            self.assertEqual("unknown", probe_model("synthetic", url, fetch)["runtime"])
        self.assertEqual("", probe_model("private\ntext", "http://localhost", fetch)["model"])

    def test_explicit_tags_and_registry_ports_are_not_latest_aliases(self):
        self.assertTrue(names_match("registry:5000/library/model", "registry:5000/library/model:latest"))
        self.assertFalse(names_match("model:q4", "model:q4:latest"))
        self.assertFalse(names_match("model:q4", "model:q8"))


if __name__ == "__main__":
    unittest.main()
