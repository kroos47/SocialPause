#!/usr/bin/env python3
"""Comparable local JVM measurements with synthetic history; not a battery benchmark."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def run(command, **kwargs):
    return subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE, **kwargs).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--baseline-ref', default='41b97118882a057805fed4229c0aee2879811114')
    parser.add_argument('--output', type=Path, required=True, help='Local ignored directory for CSV measurements')
    args = parser.parse_args()
    current = Path(__file__).resolve().parent.parent
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    args.output.mkdir(parents=True, exist_ok=True)
    paths = run(['git', '-C', str(args.repository), 'ls-tree', '-r', '--name-only', args.baseline_ref,
                 'engine/src/main/java']).splitlines()
    with tempfile.TemporaryDirectory(prefix='socialpause-perf-') as directory:
        work = Path(directory)
        for name in ['baseline', 'optimized']:
            source = work / name / 'source'
            classes = work / name / 'classes'
            source.mkdir(parents=True)
            classes.mkdir(parents=True)
            if name == 'baseline':
                for path in paths:
                    if path.endswith('.java'):
                        (source / Path(path).name).write_text(run(['git', '-C', str(args.repository), 'show', f'{args.baseline_ref}:{path}']))
            else:
                for path in (current / 'engine/src/main/java').rglob('*.java'):
                    (source / path.name).write_text(path.read_text())
            harness = current / 'engine/src/benchmark/java/SocialPausePerf.java'
            run([str(java / 'javac'), '--release', '17', '-d', str(classes), *map(str, source.glob('*.java')), str(harness)])
            for repeat in range(1, 4):
                csv = run([str(java / 'java'), '-cp', str(classes), 'SocialPausePerf', str(name == 'optimized').lower()])
                destination = args.output / f'{name}-{repeat}.csv'
                destination.write_text(csv)
                print(f'{name} measurement {repeat}: {destination}')


if __name__ == '__main__':
    main()
