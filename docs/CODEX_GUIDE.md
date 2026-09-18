# Working with Codex on this app

You describe the desired behavior. Codex reads the project, edits files, runs available checks, and reports what changed. You review the result and test it on the phone. Android Studio provides the Android editor, build integration, debugger, and device deployment; Codex works on the same source files.

## Three kinds of instructions

| Item | Purpose here |
|---|---|
| Your task message | The current request, such as fixing a paused timer. |
| `AGENTS.md` | Persistent project rules: agreed timing behavior, architecture, and verification commands. |
| `.agents/skills/socialpause-build/SKILL.md` | A reusable procedure for the specific build/test workflow. |

The conventional filename is **AGENTS.md**, with lowercase `.md`. Open the **SocialPauseBuild** folder as the Codex project so its instructions apply. Opening only its parent may not load instructions from a child directory. [Official AGENTS.md documentation](https://learn.chatgpt.com/docs/agent-configuration/agents-md)

A skill is an instruction file, not a compiler, Android library, or background process. This project has one narrow skill; separate Java, Kotlin, and Android Studio skills are unnecessary for this version. Codex can discover repository skills in `.agents/skills`; you can explicitly request `$socialpause-build`. If it does not appear, reopen the project/session. You can also point Codex directly to the file. [Official skills documentation](https://learn.chatgpt.com/docs/build-skills)

## Your first session

Open the repository root (`SocialPauseBuild/`) as the project, then ask:

> Read AGENTS.md and explain the current app structure. Show which project instructions you loaded. Do not edit files yet.

Next:

> Use $socialpause-build to verify the project. Explain any failed check and fix the underlying cause. Report the APK path and which phone tests remain unverified.

To understand the code:

> Walk me through RulesEngine.focus and advance using 5 minutes of Instagram, a screen lock, then 5 minutes of X. Explain the existing code before proposing changes.

## A useful change request

Describe an observable result, constraints, and examples:

> The usage timer should pause while the screen is locked. Reproduce any violation with a fake-clock test, fix it without changing lunch or cooldown rules, and run the build checks. Explain the files changed.

Ask Codex to inspect the current implementation before expanding scope. A short plan helps for changes involving multiple parts of the app. Routine edits do not need a long planning document.

## Review the work

1. Read the change summary and inspect the diff/file changes.
2. Check the actual build and test results. “Implemented” does not mean “ran successfully.”
3. Install the APK and run the relevant phone scenarios.
4. Report expected and actual behavior with timestamps, app names, and permission state.
5. Once satisfied, save a Git commit if you choose to initialize version control.

This folder does not require a remote repository or GitHub account to build. Git is useful later for reviewing changes and returning to a known version. A source archive is a backup, not a substitute for understanding what changed.

## Permissions and stopping points

Codex's workspace setting and OS file permissions are separate. Naming a path does not necessarily make it writable. When a command is rejected, read its stated reason and requested scope. A narrower project-local cache can sometimes solve a build problem without writing to global folders.

A normal Android app also has limits independent of Codex. No prompt, skill, or AGENTS.md can grant it Device Owner powers or guarantee Samsung live notification promotion. Treat these as engineering constraints and verify the actual phone behavior.

## Maintenance

Update AGENTS.md when you intentionally change the product rules. Update the skill only when the repeatable build workflow changes. Keep tutorials in docs/ so instruction files stay short. None of these Markdown files are packaged in the Android app.
