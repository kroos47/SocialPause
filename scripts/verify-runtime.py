#!/usr/bin/env python3
"""Run opt-in checks on a disposable emulator; adb exit zero alone is not success."""
import argparse
import re
import subprocess
import sys


def successful(output, returncode=0, minimum=1):
    counts = re.findall(r'^INSTRUMENTATION_RESULT: passed=(\d+)\s*$', output, re.M)
    codes = re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$', output, re.M)
    totals = re.findall(r'^PASS: (\d+) runtime checks\.\s*$', output, re.M)
    failure = re.search(r'FAIL:|FAILURES!!!|INSTRUMENTATION_(FAILED|ABORTED)|Process crashed', output)
    return (returncode == 0 and len(counts) == 1 and codes == ['-1'] and len(totals) == 1
            and int(counts[0]) == int(totals[0]) and int(counts[0]) >= minimum and not failure)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True, help='Disposable emulator serial, such as emulator-5580')
    parser.add_argument('--phase', choices=['checks', 'drawer', 'ui', 'optimization', 'polling'], action='append')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Synthetic tests are restricted to a disposable emulator, never a personal phone.')
    minimum = {'checks': 11, 'drawer': 8, 'ui': 3, 'optimization': 6, 'polling': 3}
    for phase in args.phase or ['checks', 'drawer', 'optimization', 'polling']:
        result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                                 '-e', 'synthetic', 'true', '-e', 'phase', phase,
                                 'app.socialpause.test/app.socialpause.RuntimeChecks'],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=300)
        print(result.stdout, end='', flush=True)
        if not successful(result.stdout, result.returncode, minimum[phase]):
            print(f'Runtime verification failed: {phase}. Inspect the instrumentation result above.', file=sys.stderr)
            return 1
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, subprocess.TimeoutExpired) as error:
        print(f'Runtime verification could not complete: {error}', file=sys.stderr)
        sys.exit(1)
