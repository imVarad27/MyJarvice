import unittest

import model_tool_router as router


class ModelToolRouterTests(unittest.TestCase):
    def test_native_schema_has_no_user_phrase_rules(self):
        schemas = router.ollama_tools()
        self.assertEqual(set(router.PHONE_TOOLS), {item["function"]["name"] for item in schemas})
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


if __name__ == "__main__":
    unittest.main()
