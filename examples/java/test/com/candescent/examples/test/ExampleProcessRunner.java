package com.candescent.examples.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs an example {@code main} in a child JVM with an isolated environment
 * (mirrors examples/typescript/test/examples.test.ts subprocess helper).
 */
final class ExampleProcessRunner {
    private ExampleProcessRunner() {}

    record RunResult(int exitCode, String stdout, String stderr) {}

    static RunResult runExample(String mainClass, Map<String, String> env) throws Exception {
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-cp", System.getProperty("java.class.path"), mainClass);
        Map<String, String> childEnv = new HashMap<>();
        String path = System.getenv("PATH");
        if (path != null) {
            childEnv.put("PATH", path);
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            childEnv.put("JAVA_HOME", javaHome);
        }
        childEnv.putAll(env);
        builder.environment().clear();
        builder.environment().putAll(childEnv);
        builder.redirectErrorStream(false);

        Process process = builder.start();
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Example timed out after 120s: " + mainClass);
        }
        return new RunResult(process.exitValue(), stdout, stderr);
    }

    private static String readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        stream.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
