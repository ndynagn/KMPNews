package com.ndynagn.kmp.news.tooling

import java.nio.file.Path
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction

/** Repackages the official executable JAR with the resolved, ordered runtime classpath. */
@TaskAction
fun packageDesktop(
    @Input runtimeClasspath: Classpath,
    @Input packager: Path,
    @Input(inferTaskDependency = false) template: Path,
    @Output artifact: Path,
) {
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    val builder = ProcessBuilder(python, packager.toString(), template.toString(), artifact.toString())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
    builder.environment()["PYTHONUTF8"] = "1"

    val process = builder.start()

    try {
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(runtimeClasspath.resolvedFiles.joinToString("\u0000"))
        }
        check(process.waitFor() == 0) { "Desktop executable JAR packaging failed" }
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}
