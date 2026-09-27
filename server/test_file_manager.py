import tempfile
import unittest
import os
from pathlib import Path
from unittest.mock import patch

import file_manager


class FileManagerSafetyTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name) / "approved"
        self.root.mkdir()
        self.child = self.root / "note.txt"
        self.child.write_text("safe", encoding="utf-8")
        self.outside = Path(self.folder.name) / "outside.txt"
        self.outside.write_text("private", encoding="utf-8")
        self.roots = patch("file_manager.get_allowed_roots", return_value=[str(self.root)])
        self.roots.start()

    def tearDown(self):
        self.roots.stop()
        self.folder.cleanup()

    def test_allows_file_under_approved_root(self):
        self.assertEqual(
            file_manager.require_allowed_existing_path(str(self.child)),
            os.path.normcase(str(self.child.resolve())),
        )

    def test_rejects_file_outside_approved_root(self):
        with self.assertRaises(PermissionError):
            file_manager.require_allowed_existing_path(str(self.outside))

    def test_rejects_unknown_shortcut_instead_of_falling_back(self):
        with self.assertRaises(ValueError):
            file_manager.browse_directory(preset="anything")


if __name__ == "__main__":
    unittest.main()
