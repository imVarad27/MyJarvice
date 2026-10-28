import unittest
import urllib.parse
from pair_helper import generate_pairing_uri, generate_pairing_payload, get_local_ips


class TestPairHelper(unittest.TestCase):

    def test_generate_pairing_uri(self):
        uri = generate_pairing_uri("192.168.1.50", "my-secret-token", port=8000)
        self.assertTrue(uri.startswith("jarvis-pair://v1?"))
        self.assertIn("host=", uri)
        self.assertIn("token=", uri)

        query = uri.split("?", 1)[1]
        params = dict(urllib.parse.parse_qsl(query))
        self.assertEqual(params["host"], "http://192.168.1.50:8000")
        self.assertEqual(params["token"], "my-secret-token")

    def test_generate_pairing_payload(self):
        payload = generate_pairing_payload("10.0.0.5", "token123", port=8000)
        self.assertEqual(payload["host"], "http://10.0.0.5:8000")
        self.assertEqual(payload["token"], "token123")

    def test_get_local_ips_returns_non_empty(self):
        ips = get_local_ips()
        self.assertIsInstance(ips, list)
        self.assertGreater(len(ips), 0)


if __name__ == "__main__":
    unittest.main()
