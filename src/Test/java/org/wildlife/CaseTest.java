package org.wildlife;


/** Dependency-free behavioural tests. Failures produce a nonzero process exit code. */
public final class CaseTest {
    @FunctionalInterface private interface Checked { void run() throws Exception; }

}

