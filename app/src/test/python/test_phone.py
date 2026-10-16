import json
import os
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0, os.environ["MD3_RUNNER_DIR"])

import md3_runner
import phone


def ok(**fields):
    reply = {"ok": True, "text": "done"}
    reply.update(fields)
    return reply


def element(number, label, role="Button", **fields):
    data = {
        "number": number, "role": role, "label": label, "x": 100, "y": 200 + number,
        "left": 0, "top": 0, "right": 200, "bottom": 400, "clickable": True, "editable": False,
        "scrollable": False, "checked": None, "focused": False, "id": None, "partial": False,
    }
    data.update(fields)
    return data


class FakeBridge:
    def __init__(self, respond=None, polls_before_reply=0):
        self.respond = respond or (lambda operation, request: ok())
        self.polls_before_reply = polls_before_reply
        self.requests = []
        self.pending = {}

    def begin(self, operation, payload):
        request = json.loads(payload)
        self.requests.append((operation, request))
        handle = len(self.requests)
        self.pending[handle] = [self.polls_before_reply, json.dumps(self.respond(operation, request))]
        return handle

    def poll(self, handle):
        state = self.pending[handle]
        if state[0] > 0:
            state[0] -= 1
            return None
        return state[1]

    @property
    def last(self):
        return self.requests[-1][1]


class BoundTest(unittest.TestCase):
    def setUp(self):
        self.bridge = FakeBridge()
        phone.attach(self.bridge)
        self.addCleanup(phone.detach)

    def acted(self):
        operation, request = self.bridge.requests[-1]
        self.assertEqual("act", operation)
        return request


class ActionTest(BoundTest):
    def test_tap_by_text(self):
        phone.tap("Wi-Fi")

        self.assertEqual({"action": "tap", "observe": "none", "target": {"text": "Wi-Fi"}}, self.acted())

    def test_tap_by_keywords(self):
        phone.tap(text="Search", role="Button", exact=True, index=2, id="search_button")

        self.assertEqual({"text": "Search", "role": "Button", "exact": True, "index": 2, "id": "search_button"}, self.acted()["target"])

    def test_tap_by_number_or_element(self):
        phone.tap(7)
        self.assertEqual(7, self.acted()["element"])

        phone.tap(phone.Element(element(12, "Save")))
        self.assertEqual(12, self.acted()["element"])

    def test_tap_by_a_raw_target(self):
        phone.tap({"text": "Done", "exact": True})

        self.assertEqual({"text": "Done", "exact": True}, self.acted()["target"])

    def test_tap_needs_something_to_tap(self):
        with self.assertRaises(ValueError):
            phone.tap()
        with self.assertRaises(ValueError):
            phone.tap("A", text="B")
        with self.assertRaises(TypeError):
            phone.tap(3.5)
        self.assertEqual([], self.bridge.requests)

    def test_say_travels_with_the_action(self):
        phone.long_press("Photo", say="Holding the photo")
        self.assertEqual("long_press", self.acted()["action"])
        self.assertEqual("Holding the photo", self.acted()["say"])

        phone.double_tap("Photo")
        self.assertEqual("double_tap", self.acted()["action"])
        self.assertNotIn("say", self.acted())

    def test_tap_at_takes_numbers(self):
        phone.tap_at(10.0, "20")

        self.assertEqual({"action": "tap_at", "observe": "none", "x": 10, "y": 20}, self.acted())

    def test_typing(self):
        phone.type_text("hello", "Search", submit=True)
        self.assertEqual({"action": "type", "observe": "none", "text": "hello", "submit": True, "target": {"text": "Search"}}, self.acted())

        phone.type_text("again")
        self.assertEqual({"action": "type", "observe": "none", "text": "again"}, self.acted())
        self.assertIs(phone.type, phone.type_text)

    def test_scrolling(self):
        phone.scroll("down", "Messages")
        self.assertEqual({"action": "scroll", "observe": "none", "direction": "down", "target": {"text": "Messages"}}, self.acted())

        phone.scroll("up")
        self.assertNotIn("target", self.acted())

    def test_scroll_to_looks_for_a_selector(self):
        phone.scroll_to("Advanced", direction="down", max_swipes=5)
        self.assertEqual({"action": "scroll_to", "observe": "none", "direction": "down", "max_swipes": 5, "target": {"text": "Advanced"}}, self.acted())

        with self.assertRaises(ValueError):
            phone.scroll_to()
        with self.assertRaises(ValueError):
            phone.scroll_to(4)

    def test_swiping_in_seconds(self):
        phone.swipe("up", distance=0.5, duration=0.4)
        self.assertEqual({"action": "swipe", "observe": "none", "direction": "up", "distance": 0.5, "duration_ms": 400}, self.acted())

        phone.swipe(start=(100, 900), end=(100.0, 300))
        self.assertEqual({"x": 100, "y": 900}, self.acted()["from"])
        self.assertEqual({"x": 100, "y": 300}, self.acted()["to"])

    def test_dragging(self):
        phone.drag([(10, 20), (30, 40), (50, 60)], duration=1.5)

        self.assertEqual([{"x": 10, "y": 20}, {"x": 30, "y": 40}, {"x": 50, "y": 60}], self.acted()["path"])
        self.assertEqual(1500, self.acted()["duration_ms"])

    def test_buttons_and_apps(self):
        phone.press("back")
        self.assertEqual({"action": "press", "observe": "none", "key": "back"}, self.acted())

        phone.open_app("Settings", say="Opening Settings")
        self.assertEqual({"action": "open_app", "observe": "none", "app": "Settings", "say": "Opening Settings"}, self.acted())

    def test_long_waits_are_split_into_allowed_pieces(self):
        phone.wait(22.5)

        self.assertEqual([10000, 10000, 2500], [request["ms"] for _, request in self.bridge.requests])
        phone.wait(0)
        self.assertEqual(3, len(self.bridge.requests))

    def test_the_result_carries_what_the_phone_reported(self):
        self.bridge.respond = lambda operation, request: ok(text="Tapped [3]", changed=True, screen="App: x")

        result = phone.tap("Wi-Fi")

        self.assertTrue(result)
        self.assertTrue(result.changed)
        self.assertEqual("Tapped [3]", str(result))
        self.assertEqual("App: x", result.screen)

    def test_a_failed_action_raises_with_the_reason(self):
        self.bridge.respond = lambda operation, request: {"ok": False, "text": "failed: No element matches text \"x\""}

        with self.assertRaises(phone.PhoneError) as caught:
            phone.tap("x")

        self.assertIn("No element matches", str(caught.exception))

    def test_a_refused_request_raises_with_the_message(self):
        self.bridge.respond = lambda operation, request: {"ok": False, "error": "Phone control is off"}

        with self.assertRaises(phone.PhoneError) as caught:
            phone.press("home")

        self.assertEqual("Phone control is off", str(caught.exception))

    def test_do_passes_raw_fields(self):
        phone.do("press", key="home", say="Home")

        self.assertEqual({"action": "press", "observe": "none", "key": "home", "say": "Home"}, self.acted())

    def test_replies_that_take_a_while_are_waited_for(self):
        self.bridge.polls_before_reply = 5
        started = time.monotonic()

        phone.press("back")

        self.assertGreaterEqual(time.monotonic() - started, phone.POLL_SECONDS * 4)
        self.assertEqual(1, len(self.bridge.requests))


class WaitForTest(BoundTest):
    def test_waiting_for_text(self):
        result = phone.wait_for("Connected", timeout=8)

        self.assertTrue(result)
        self.assertEqual({"action": "wait_for", "observe": "none", "condition": "text_visible", "text": "Connected", "timeout_ms": 8000}, self.acted())

    def test_the_other_conditions(self):
        phone.wait_for(gone="Loading")
        self.assertEqual(("text_gone", "Loading", 10000), (self.acted()["condition"], self.acted()["text"], self.acted()["timeout_ms"]))

        phone.wait_for(app="settings", timeout=3)
        self.assertEqual(("app", "settings"), (self.acted()["condition"], self.acted()["app"]))

        phone.wait_for(idle=True)
        self.assertEqual("idle", self.acted()["condition"])

        phone.wait_for(changed=True)
        self.assertEqual("screen_changed", self.acted()["condition"])

    def test_exactly_one_condition_is_required(self):
        with self.assertRaises(ValueError):
            phone.wait_for()
        with self.assertRaises(ValueError):
            phone.wait_for("a", gone="b")

    def test_a_timeout_is_falsy_and_does_not_raise(self):
        self.bridge.respond = lambda operation, request: {"ok": False, "text": "failed: Timed out after 1000 ms"}

        result = phone.wait_for("Never", timeout=1)

        self.assertFalse(result)
        self.assertIn("Timed out", result.text)

    def test_a_refused_request_still_raises(self):
        self.bridge.respond = lambda operation, request: {"ok": False, "error": "wait_for needs timeout_ms"}

        with self.assertRaises(phone.PhoneError):
            phone.wait_for("x")


class ScreenTest(BoundTest):
    def setUp(self):
        super().setUp()
        self.bridge.respond = lambda operation, request: ok(
            app="com.android.settings", width=1080, height=2400, text="App: com.android.settings",
            elements=[
                element(1, "Wi-Fi · Connected", "Item", id="wifi"),
                element(2, "Wi-Fi", "Switch", checked=True),
                element(3, "Bluetooth", "Switch", checked=False),
            ],
        )

    def test_a_screen_reads_into_objects(self):
        screen = phone.screen(detailed=True, say="Looking")

        self.assertEqual("screen", self.bridge.requests[-1][0])
        self.assertEqual({"detailed": True, "say": "Looking"}, self.bridge.last)
        self.assertEqual("com.android.settings", screen.app)
        self.assertEqual((1080, 2400), (screen.width, screen.height))
        self.assertEqual("App: com.android.settings", str(screen))
        self.assertEqual([1, 2, 3], [item.number for item in screen.elements])
        self.assertTrue(screen.elements[1].checked)
        self.assertEqual("wifi", screen.elements[0].id)

    def test_searching_a_screen(self):
        screen = phone.screen()

        self.assertTrue(screen.has("wi-fi"))
        self.assertTrue(screen.has("Connected"))
        self.assertTrue(screen.has("Connected", exact=True))
        self.assertFalse(screen.has("Wi", exact=True))
        self.assertTrue(screen.has("Wi-Fi", exact=True))
        self.assertEqual([2, 3], [item.number for item in screen.find(role="switch")])
        self.assertEqual([1], [item.number for item in screen.find(id="com.android.settings:id/wifi")])
        self.assertEqual([], screen.find("nothing"))

    def test_finding_sends_the_selector_to_the_phone(self):
        found = phone.find("Wi-Fi", role="Switch", exact=True)

        self.assertEqual({"detailed": False, "find": {"text": "Wi-Fi", "role": "Switch", "exact": True}}, self.bridge.last)
        self.assertEqual(3, len(found))
        self.assertTrue(phone.exists("Wi-Fi"))

    def test_finding_needs_something_to_find(self):
        with self.assertRaises(ValueError):
            phone.find()

    def test_say_shows_a_caption(self):
        phone.say("Almost there")

        self.assertEqual(("say", {"text": "Almost there"}), self.bridge.requests[-1])


class StopTest(BoundTest):
    def test_a_stopped_script_gets_an_interrupt_that_scripts_do_not_swallow(self):
        self.bridge.respond = lambda operation, request: {"ok": False, "cancelled": True, "error": "Stopped by the user"}

        with self.assertRaises(phone.Cancelled):
            phone.tap("x")
        self.assertTrue(issubclass(phone.Cancelled, KeyboardInterrupt))
        self.assertFalse(issubclass(phone.Cancelled, Exception))

        swallowed = []
        try:
            try:
                phone.tap("x")
            except Exception:
                swallowed.append(True)
        except phone.Cancelled:
            pass
        self.assertEqual([], swallowed)


class BindingTest(unittest.TestCase):
    def test_without_a_bridge_the_phone_is_off_limits(self):
        phone.detach()

        with self.assertRaises(PermissionError) as caught:
            phone.tap("x")

        self.assertIn("Python can control the phone", str(caught.exception))

    def test_a_bridge_belongs_to_the_thread_that_got_it(self):
        phone.attach(FakeBridge())
        self.addCleanup(phone.detach)
        outcome = []

        def other():
            try:
                phone.press("back")
            except PermissionError:
                outcome.append("refused")

        thread = threading.Thread(target=other)
        thread.start()
        thread.join(2)

        self.assertEqual(["refused"], outcome)
        phone.press("back")


class RoutineTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        previous = os.getcwd()
        os.chdir(self.folder.name)
        self.addCleanup(os.chdir, previous)
        os.makedirs("routines")
        with open("routines/greet.py", "w") as handle:
            handle.write("phone.tap(params['target'])\nresult = 'tapped ' + params['target']\n")
        with open("routines/quiet.py", "w") as handle:
            handle.write("x = 1\n")
        self.bridge = FakeBridge()
        phone.attach(self.bridge)
        self.addCleanup(phone.detach)

    def test_routines_are_listed_by_name(self):
        self.assertEqual(["greet", "quiet"], phone.routines())

    def test_no_folder_means_no_routines(self):
        with tempfile.TemporaryDirectory() as empty:
            os.chdir(empty)

            self.assertEqual([], phone.routines())

    def test_a_routine_runs_with_params_and_the_same_phone(self):
        result = phone.routine("greet", target="OK")

        self.assertEqual("tapped OK", result)
        self.assertEqual({"text": "OK"}, self.bridge.last["target"])
        self.assertIsNone(phone.routine("quiet"))

    def test_an_unknown_routine_lists_the_saved_ones(self):
        with self.assertRaises(phone.PhoneError) as caught:
            phone.routine("missing")

        self.assertIn("greet, quiet", str(caught.exception))

    def test_a_routine_name_cannot_walk_out_of_the_folder(self):
        for name in ("../secret", "a/b", "", "x.py"):
            with self.assertRaises(phone.PhoneError):
                phone.routine(name)


class RunnerTest(unittest.TestCase):
    def run_script(self, code, bridge, timeout=5):
        script = md3_runner.start(code, tempfile.gettempdir(), bridge)
        return script, script.wait(timeout)

    def test_a_script_drives_the_phone_through_the_bridge(self):
        bridge = FakeBridge()

        _, (output, failed, timed_out) = self.run_script("import phone\nphone.tap('OK')\nphone.press('back')\nprint('done')", bridge)

        self.assertEqual("done\n", output)
        self.assertFalse(failed)
        self.assertEqual(["tap", "press"], [request["action"] for _, request in bridge.requests])

    def test_without_permission_the_script_is_told_why(self):
        _, (output, failed, _) = self.run_script("import phone\nphone.tap('OK')", None)

        self.assertTrue(failed)
        self.assertIn("Python can control the phone", output)

    def test_stopping_ends_a_script_waiting_for_the_phone(self):
        bridge = FakeBridge()
        bridge.poll = lambda handle: None
        script = md3_runner.start("import phone\nphone.tap('OK')\nprint('after')", tempfile.gettempdir(), bridge)
        time.sleep(0.3)

        script.cancel()
        script.worker.join(3)

        self.assertFalse(script.worker.is_alive())
        self.assertNotIn("after", script.output.getvalue())

    def test_the_phone_binding_is_gone_once_the_script_ends(self):
        bridge = FakeBridge()
        script, _ = self.run_script("import phone\nphone.press('back')", bridge)

        with self.assertRaises(PermissionError):
            phone.press("back")

    def test_a_script_that_catches_errors_still_stops_on_cancel(self):
        bridge = FakeBridge(lambda operation, request: {"ok": False, "cancelled": True, "error": "Stopped by the user"})
        code = "import phone\ntry:\n    phone.tap('x')\nexcept Exception:\n    print('swallowed')\nprint('after')"

        _, (output, failed, _) = self.run_script(code, bridge)

        self.assertTrue(failed)
        self.assertNotIn("swallowed", output)
        self.assertNotIn("after", output)


if __name__ == "__main__":
    unittest.main()
