#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('wrapper', Path(__file__).with_name('verify-runtime.py'))
wrapper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wrapper)
GOOD = 'INSTRUMENTATION_RESULT: passed=11\nINSTRUMENTATION_RESULT: stream=\nPASS example\n\nPASS: 11 runtime checks.\n\nINSTRUMENTATION_CODE: -1\n'

class WrapperChecks(unittest.TestCase):
    def test_success(self):
        self.assertTrue(wrapper.successful(GOOD, 0, 11))
    def test_adb_zero_with_failed_test(self):
        self.assertFalse(wrapper.successful(GOOD.replace('PASS example', 'FAIL: assertion failed'), 0))
    def test_cancelled_instrumentation(self):
        self.assertFalse(wrapper.successful(GOOD.replace('CODE: -1', 'CODE: 0'), 0))
    def test_missing_summary(self):
        self.assertFalse(wrapper.successful('INSTRUMENTATION_CODE: -1\n', 0))
    def test_crashed_process(self):
        self.assertFalse(wrapper.successful(GOOD+'INSTRUMENTATION_FAILED: Process crashed\n', 0))
    def test_adb_error(self):
        self.assertFalse(wrapper.successful(GOOD, 1))
    def test_incomplete_suite(self):
        self.assertFalse(wrapper.successful(GOOD, 0, 19))
    def test_inconsistent_counts(self):
        self.assertFalse(wrapper.successful(GOOD.replace('passed=11', 'passed=8'), 0))

if __name__ == '__main__':
    unittest.main()
