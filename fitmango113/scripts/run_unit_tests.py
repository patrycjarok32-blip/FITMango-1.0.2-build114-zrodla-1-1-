"""Bound the CI test stage and retain JVM stacks when Gradle stops progressing."""
import argparse
import os
from pathlib import Path
import signal
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tests", action="append", default=[], help="Gradle test filter; repeat to select several classes")
    args = parser.parse_args()
    filters = [argument for pattern in args.tests for argument in ("--tests", pattern)]
    report = Path("app/build/reports/ci")
    report.mkdir(parents=True, exist_ok=True)
    process = subprocess.Popen([
        "./gradlew", "--no-daemon", "--console=plain", "--stacktrace",
        ":app:testDebugUnitTest", *filters,
    ], start_new_session=True)
    try:
        return process.wait(timeout=18 * 60)
    except subprocess.TimeoutExpired:
        print("::error::Testy przekroczyły 18 minut. Zapisuję stosy JVM do raportów CI.", flush=True)
        worker_pids = []
        try:
            listing = subprocess.check_output(["jcmd", "-l"], text=True, timeout=10)
            for line in listing.splitlines():
                pid, _, command = line.partition(" ")
                if not pid.isdigit() or not any(name in command for name in ("GradleWorkerMain", "GradleDaemon", "GradleWrapperMain")):
                    continue
                worker_pids.append(int(pid))
                with (report / f"jvm-{pid}-threads.txt").open("w") as output:
                    try:
                        subprocess.run(["jcmd", pid, "Thread.print", "-l"], stdout=output, stderr=subprocess.STDOUT, timeout=15)
                    except subprocess.TimeoutExpired:
                        output.write("Thread dump timed out.\n")
        except (OSError, subprocess.SubprocessError) as error:
            (report / "diagnostic-error.txt").write_text(type(error).__name__)
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        for pid in worker_pids:
            try:
                os.kill(pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        return 124


if __name__ == "__main__":
    sys.exit(main())
