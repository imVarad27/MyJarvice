import sys
import unittest
from unittest.mock import Mock, patch

import pc_controller


class PcControllerTests(unittest.TestCase):
    def test_launcher_rejects_shell_commands_before_any_os_call(self):
        with patch.object(pc_controller.subprocess, "Popen") as process, patch.object(pc_controller.os, "startfile", create=True) as start:
            for value in ("notepad & calc", "powershell -Command bad", "C:/private/data", "https://example.com"):
                with self.assertRaises(ValueError):
                    pc_controller.launch_pc_application(value)
            process.assert_not_called()
            start.assert_not_called()

    def test_allowlisted_program_never_uses_shell_interpolation(self):
        with patch.object(pc_controller.shutil, "which", return_value="C:/Windows/notepad.exe"):
            with patch.object(pc_controller.subprocess, "Popen") as process:
                pc_controller.launch_pc_application("notepad")
                process.assert_called_once_with(["notepad.exe"], shell=False)

    def test_launch_error_is_not_a_success_result(self):
        with patch.object(pc_controller.shutil, "which", return_value=None):
            with patch.object(pc_controller.os, "startfile", side_effect=OSError("not found"), create=True):
                with self.assertRaises(RuntimeError):
                    pc_controller.launch_pc_application("spotify")

    def test_volume_error_is_not_fabricated_success(self):
        driver = Mock()
        driver.AudioUtilities.GetSpeakers.side_effect = RuntimeError("driver unavailable")
        with patch.dict(sys.modules, {"pycaw.pycaw": driver}):
            with self.assertRaises(RuntimeError):
                pc_controller.set_master_volume(40)


if __name__ == "__main__":
    unittest.main()
