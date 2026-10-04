import unittest

import model_tool_router as router


class ModelToolRouterTests(unittest.TestCase):
    def test_native_schema_has_no_user_phrase_rules(self):
        schemas = router.ollama_tools()
        self.assertEqual(set(router.ALL_TOOLS), {item["function"]["name"] for item in schemas})
        self.assertEqual([], schemas[0]["function"]["parameters"]["required"])

    def test_accepts_valid_native_tool_call(self):
        message = {"tool_calls": [{"function": {"name": "phone_status", "arguments": {}}}]}
        self.assertEqual(router.ToolDecision("phone_status", ""), router.decision_from_message(message))

    def test_validates_action_arguments(self):
        message = {"tool_calls": [{"function": {
            "name": "open_phone_app", "arguments": {"app": "YouTube"}
        }}]}
        decision = router.decision_from_message(message)
        self.assertEqual({"type": "OPEN_APP", "query": "YouTube"}, router.android_action(decision))
        invalid = [
            {"tool_calls": [{"function": {"name": "flashlight", "arguments": {"state": "destroy"}}}]},
            {"tool_calls": [{"function": {"name": "call_phone", "arguments": {}}}]},
            {"tool_calls": [{"function": {"name": "shell", "arguments": {"command": "dir"}}}]},
            {"tool_calls": [{"function": {"name": "phone_status", "arguments": {"extra": "x"}}}]},
            {"tool_calls": []},
        ]
        for value in invalid:
            self.assertIsNone(router.decision_from_message(value), value)

    def test_typed_host_arguments_and_shell_injection_are_rejected(self):
        def call(name, args):
            return router.decision_from_message({"tool_calls": [{"function": {"name": name, "arguments": args}}]})
        self.assertEqual({"percent": 45}, call("pc_volume", {"percent": 45}).arguments)
        for args in ({"percent": True}, {"percent": "45"}, {"percent": 101}, {"percent": -1}, {"percent": 45, "cmd": "evil"}):
            self.assertIsNone(call("pc_volume", args))
        self.assertIsNone(call("open_pc_app", {"app": "notepad & calc"}))
        self.assertIsNone(call("open_pc_folder", {"folder": "C:/"}))
        self.assertIsNone(call("remember", {"fact": "\u0000"}))
        self.assertIsNone(call([], {}))

    def test_read_only_schema_excludes_mutations_and_draft_calls(self):
        names = {item["function"]["name"] for item in router.ollama_tools(read_only=True)}
        self.assertIn("pc_status", names)
        self.assertIn("phone_status", names)
        self.assertNotIn("add_task", names)
        self.assertNotIn("draft_email", names)
        self.assertNotIn("call_phone", names)


if __name__ == "__main__":
    unittest.main()
