import * as fs from 'fs';
import * as path from 'path';
import {
    commands, extensions, ExtensionContext, ProcessExecution, ShellExecution, Task, TaskPanelKind, TaskRevealKind, tasks,
    TaskScope, Uri, workspace
} from 'vscode';

/**
 * The language server attaches these commands to the code lenses that generate the Spring AOT metadata (see
 * `VSCodeBuildCommandProvider`). They take the arguments of `maven.goal.custom` and `gradle.runBuild`: the build file,
 * the goals or tasks, the environment. Each one runs the command of Maven for Java or Gradle for Java when that
 * extension is installed, and the same build as a task otherwise, for example next to "Java and Kotlin by
 * IntelliJ IDEA".
 */
const MAVEN_EXTENSION_ID = 'vscjava.vscode-maven';
const GRADLE_EXTENSION_ID = 'vscjava.vscode-gradle';
const IS_WINDOWS = process.platform === 'win32';

export function registerBuildCommands(context: ExtensionContext): void {
    context.subscriptions.push(
        commands.registerCommand('vscode-spring-boot.maven.goal', (pom: string, goal: string, env: Record<string, string> = {}) =>
            extensions.getExtension(MAVEN_EXTENSION_ID)
                ? commands.executeCommand('maven.goal.custom', pom, goal, env)
                : runBuild('Spring Boot: Maven', pom, findUp(path.dirname(pom), IS_WINDOWS ? 'mvnw.cmd' : 'mvnw') ?? 'mvn',
                    [...(goal.match(/\S+/g) ?? []), '-f', pom], env)),
        // Gradle for Java runs the build in the project of the build file, a subproject for example, which is what `-p` does
        commands.registerCommand('vscode-spring-boot.gradle.build', (buildFile: string, command: string, env: Record<string, string> = {}) =>
            extensions.getExtension(GRADLE_EXTENSION_ID)
                ? commands.executeCommand('gradle.runBuild', buildFile, command, env)
                : runBuild('Spring Boot: Gradle', buildFile, findUp(path.dirname(buildFile), IS_WINDOWS ? 'gradlew.bat' : 'gradlew') ?? 'gradle',
                    [...(command.match(/\S+/g) ?? []), '-p', path.dirname(buildFile)], env))
    );
}

/** Runs the build as a task in the folder of the build file. */
async function runBuild(name: string, buildFile: string, executable: string, args: string[], env: Record<string, string>): Promise<void> {
    const options = { cwd: path.dirname(buildFile), env };
    // Without a shell the environment reaches the build as it is, shell startup files (SDKMAN, for example) cannot
    // change JAVA_HOME. On Windows the wrapper scripts and `mvn` are batch files, which need a shell.
    const execution = IS_WINDOWS
        ? new ShellExecution(executable, args, options)
        : new ProcessExecution(executable, args, options);
    const task = new Task({ type: IS_WINDOWS ? 'shell' : 'process' }, workspace.getWorkspaceFolder(Uri.file(buildFile)) ?? TaskScope.Workspace,
        name, 'Spring Boot', execution, []);
    task.presentationOptions = { reveal: TaskRevealKind.Always, panel: TaskPanelKind.Dedicated, clear: true };
    await tasks.executeTask(task);
}

/** The file in `dir` or the closest parent folder that has it, the lookup Maven for Java uses for `mvnw`. */
function findUp(dir: string, fileName: string): string | undefined {
    while (!fs.existsSync(path.join(dir, fileName))) {
        const parent = path.dirname(dir);
        if (parent === dir) {
            return undefined;
        }
        dir = parent;
    }
    return path.join(dir, fileName);
}
