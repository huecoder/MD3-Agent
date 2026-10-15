import os
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0, os.environ["MD3_RUNNER_DIR"])

import md3_runner


def run_in_background(code, timeout):
    script = md3_runner.start(code, tempfile.gettempdir())
    result = {}

    def wait():
        result["value"] = script.wait(timeout)
        result["at"] = time.monotonic()

    waiter = threading.Thread(target=wait)
    waiter.start()
    return script, waiter, result


class ScriptTest(unittest.TestCase):
    def test_returns_what_the_script_printed(self):
        script = md3_runner.start("print('hello')\nimport sys\nprint('warn', file=sys.stderr)", tempfile.gettempdir())

        output, failed, timed_out = script.wait(5)

        self.assertEqual("hello\nwarn\n", output)
        self.assertFalse(failed)
        self.assertFalse(timed_out)

    def test_reports_an_exception_as_a_failure(self):
        script = md3_runner.start("raise ValueError('boom')", tempfile.gettempdir())

        output, failed, timed_out = script.wait(5)

        self.assertIn("ValueError: boom", output)
        self.assertTrue(failed)
        self.assertFalse(timed_out)

    def test_a_nonzero_exit_is_a_failure(self):
        script = md3_runner.start("import sys\nsys.exit(3)", tempfile.gettempdir())

        output, failed, _ = script.wait(5)

        self.assertIn("Exited with code 3", output)
        self.assertTrue(failed)

    def test_runs_in_the_given_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            script = md3_runner.start("import os\nprint(os.getcwd())", folder)

            output, _, _ = script.wait(5)

            self.assertEqual(os.path.realpath(folder), os.path.realpath(output.strip()))

    def test_a_script_that_runs_too_long_is_stopped(self):
        script = md3_runner.start("import time\nwhile True:\n    time.sleep(0.01)", tempfile.gettempdir())
        started = time.monotonic()

        _, _, timed_out = script.wait(0.3)

        self.assertTrue(timed_out)
        self.assertLess(time.monotonic() - started, 3)
        script.worker.join(3)
        self.assertFalse(script.worker.is_alive())


class CancelTest(unittest.TestCase):
    def test_cancel_returns_the_waiter_at_once_even_in_a_long_sleep(self):
        script, waiter, result = run_in_background("import time\ntime.sleep(60)\nprint('after')", timeout=120)
        time.sleep(0.3)
        cancelled_at = time.monotonic()

        script.cancel()
        waiter.join(2)

        self.assertFalse(waiter.is_alive(), "wait() did not return after cancel")
        self.assertLess(result["at"] - cancelled_at, 1)
        self.assertNotIn("after", result["value"][0])
        self.assertFalse(result["value"][2])

    def test_a_cancelled_script_stops_running_once_its_sleep_ends(self):
        script, waiter, result = run_in_background("import time\ntime.sleep(1)\nprint('after')", timeout=120)
        time.sleep(0.2)

        script.cancel()
        waiter.join(2)
        script.worker.join(4)

        self.assertFalse(script.worker.is_alive())
        self.assertNotIn("after", script.output.getvalue())

    def test_a_busy_loop_is_cancelled(self):
        script, waiter, _ = run_in_background("while True:\n    pass", timeout=120)
        time.sleep(0.2)

        script.cancel()
        waiter.join(2)
        script.worker.join(3)

        self.assertFalse(waiter.is_alive())
        self.assertFalse(script.worker.is_alive())

    def test_cancelling_before_waiting_returns_immediately(self):
        script = md3_runner.start("import time\ntime.sleep(30)", tempfile.gettempdir())
        script.cancel()
        started = time.monotonic()

        script.wait(120)

        self.assertLess(time.monotonic() - started, 1)

    def test_a_lingering_cancelled_script_does_not_take_over_the_next_ones_output(self):
        first, first_waiter, _ = run_in_background("import time\ntime.sleep(1)\nprint('first, too late')", timeout=120)
        time.sleep(0.2)
        first.cancel()
        first_waiter.join(2)

        second = md3_runner.start("import time\nprint('one')\ntime.sleep(2)\nprint('two')", tempfile.gettempdir())
        output, failed, _ = second.wait(10)

        self.assertEqual("one\ntwo\n", output)
        self.assertFalse(failed)
        self.assertNotIn("first", output)

    def test_output_from_threads_the_script_started_is_kept(self):
        code = "import threading\nthread = threading.Thread(target=lambda: print('from thread'))\nthread.start()\nthread.join()"
        script = md3_runner.start(code, tempfile.gettempdir())

        output, _, _ = script.wait(5)

        self.assertEqual("from thread\n", output)


if __name__ == "__main__":
    unittest.main()
